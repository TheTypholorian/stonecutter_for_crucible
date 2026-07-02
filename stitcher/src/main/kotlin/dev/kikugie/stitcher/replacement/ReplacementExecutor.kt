package dev.kikugie.stitcher.replacement

import dev.kikugie.commons.collections.present
import dev.kikugie.stitcher.issue.ProblemID
import dev.kikugie.stitcher.issue.ProblemReporter
import dev.kikugie.stitcher.issue.report
import org.pcre4j.Pcre2MatchData
import org.pcre4j.option.Pcre2SubstituteOption.EXTENDED
import org.pcre4j.option.Pcre2SubstituteOption.REPLACEMENT_ONLY
import org.pcre4j.regex.appendMatch
import java.util.*
import java.util.regex.MatchResult

private val REPLACEMENT_GROUP = ProblemID("replacement-execution")

internal interface ReplacementExecutorBase {
    val identifiers: Set<String>
    val Replacement.active: Boolean
        get() = identifier == null || identifier in identifiers

    fun collect(offset: Int, text: CharSequence, queue: EmitQueue)

    fun replace(offset: Int, builder: StringBuilder) {
        val queue = EmitQueue()
        collect(offset, builder, queue)

        var offset = 0
        for (emit in queue.entries)
            offset += emit.apply(builder, offset)
    }
}

internal class ReplacementExecutor(replacements: List<Replacement>, problems: ProblemReporter) : ReplacementExecutorBase {
    override val identifiers: MutableSet<String> = HashSet()
    private val stringExecutor: StringExecutor = StringExecutor(replacements, problems, identifiers)
    private val regexExecutor: RegexExecutor = RegexExecutor(replacements, problems, identifiers)
    private val perlExecutor: PerlExecutor = PerlExecutor(replacements, problems, identifiers)

    init {
        for (repl in replacements) if (repl.identifier != null && repl.enabled)
            identifiers += repl.identifier!!
    }

    override fun collect(offset: Int, text: CharSequence, queue: EmitQueue) {
        stringExecutor.collect(offset, text, queue)
        regexExecutor.collect(offset, text, queue)
        perlExecutor.collect(offset, text, queue)
    }

    fun toggle(enable: Set<String> = emptySet(), disable: Set<String> = emptySet()) {
        identifiers += enable
        identifiers -= disable
        stringExecutor.reset()
    }

    fun include(new: Iterable<StringReplacement>) {
        stringExecutor.include(new)
    }
}

private class StringExecutor(
    replacements: List<Replacement>,
    private val problems: ProblemReporter,
    override val identifiers: Set<String>,
) : ReplacementExecutorBase {
    private val replacements = mutableMapOf<String, MutableList<StringReplacement>>()
        .apply { replacements.byPattern(this) }
    private val searcher = AhoCorasick(replacements.toPayloads())
    private val cache = mutableMapOf<String, String?>()

    override fun collect(offset: Int, text: CharSequence, queue: EmitQueue) {
        for (match in searcher.search(text)) {
            val value = match.findReplacement(offset)
            if (value != null) queue += StringEmit(match, value)
        }
    }

    fun reset() {
        cache.clear()
    }

    fun include(new: Iterable<StringReplacement>) {
        new.byPattern(replacements)
        searcher += new.toPayloads()
        cache.clear()
    }

    private fun Iterable<Replacement>.byPattern(map: MutableMap<String, MutableList<StringReplacement>>) {
        for (repl in this) if (repl is StringReplacement) {
            map.getOrPut(repl.pattern, ::mutableListOf) += repl
            map.getOrPut(repl.target, ::mutableListOf) += repl
        }
    }

    private fun Iterable<Replacement>.toPayloads() = buildSet {
        for (entry in this@toPayloads) if (entry is StringReplacement) {
            this += entry.pattern
            this += entry.target
        }
    }

    private tailrec fun AhoCorasick.Match.findReplacement(offset: Int, prev: String = this.pattern): String? =
        when (val next = nextTransition(offset, pattern)) {
            prev, pattern -> next
            null -> if (prev == pattern) null else prev
            else -> findReplacement(offset, next)
        }

    private fun AhoCorasick.Match.nextTransition(offset: Int, pattern: String): String? {
        val variants = replacements[pattern]?.takeIf(List<*>::isNotEmpty)
            ?: return null

        val candidates = buildSet {
            for (it in variants) if (it.active)
                this += it.target
        }

        if (candidates.size > 1) reportAmbiguousTransition(offset, pattern, candidates)
        return candidates.singleOrNull()
    }

    private fun AhoCorasick.Match.reportAmbiguousTransition(offset: Int, prev: String, candidates: Set<String>) = problems.report(
        REPLACEMENT_GROUP + "ambiguous-transition"
    ) {
        loc = doc.locate(offset + start)
        label = "Ambiguous replacement"
        info = buildString {
            append("Pattern $pattern")
            if (prev != pattern) append(" (through $prev)")
            appendLine(" can be replaced with multiple values:\n")
            append(candidates.present { "'$it'" })
            append('.')
        }
    }

    private class StringEmit(val match: AhoCorasick.Match, val value: String) : EmitQueue.Entry {
        override val start: Int get() = match.start
        override val end: Int get() = match.end

        override fun apply(builder: StringBuilder, offset: Int): Int {
            builder.replace(start + offset, end + offset, value)
            return value.length - (end - start)
        }
    }
}

private class RegexExecutor(
    replacements: List<Replacement>,
    private val problems: ProblemReporter,
    override val identifiers: Set<String>,
) : ReplacementExecutorBase {
    private val replacements = replacements.filterIsInstance<RegexReplacement>()

    override fun collect(offset: Int, text: CharSequence, queue: EmitQueue) {
        for (repl in replacements) if (repl.active)
            process(repl, text, queue)
    }

    private fun process(repl: RegexReplacement, text: CharSequence, queue: EmitQueue) {
        val matcher = repl.regex.matcher(text)
        var index = 0
        while (index >= 0 && matcher.find(index)) {
            queue += RegexEmit(matcher.toMatchResult(), repl.target)
            index = matcher.nextIndex(text)
        }
    }

    private fun MatchResult.nextIndex(text: CharSequence): Int {
        val next = end() + if (end() == start()) 1 else 0
        return if (next <= text.length) next else -1
    }

    private class RegexEmit(val match: MatchResult, val value: String) : EmitQueue.Entry {
        override val start: Int get() = match.start()
        override val end: Int get() = match.end()

        override fun apply(builder: StringBuilder, offset: Int): Int {
            val length = builder.length
            builder.appendMatch(match, value)

            val repl = builder.substring(length)
            if (repl.isEmpty()) return 0

            builder.setLength(length)
            builder.replace(start + offset, end + offset, repl)

            return builder.length - length
        }
    }
}


private class PerlExecutor(
    replacements: List<Replacement>,
    private val problems: ProblemReporter,
    override val identifiers: Set<String>,
) : ReplacementExecutorBase {
    private val replacements = replacements.filterIsInstance<PerlReplacement>()

    override fun collect(offset: Int, text: CharSequence, queue: EmitQueue) {
        val string = text.toString()
        for (repl in replacements) if (repl.active)
            process(repl, offset, string, queue)
    }

    private fun process(repl: PerlReplacement, offset: Int, text: String, queue: EmitQueue) {
        val data = Pcre2MatchData(repl.regex)
        var start = 0
        while (start in text.indices) {
            val value = substitute(repl, offset, start, text, data)
            if (value.isEmpty()) break

            val offsets = data.ovector()
            queue += PerlEmit(offsets[0].toInt(), offsets[1].toInt(), value)
            start = offsets[1].toInt()
        }
    }

    private fun substitute(repl: PerlReplacement, offset: Int, start: Int, text: String, data: Pcre2MatchData): String = try {
        repl.regex.substitute(text, start, EnumSet.of(EXTENDED, REPLACEMENT_ONLY), data, null, repl.target)
    } catch (e: RuntimeException) {
        reportSubstituteError(offset + start, e)
        ""
    }

    private fun reportSubstituteError(index: Int, error: Throwable): Unit = problems.report(REPLACEMENT_GROUP + "pcre-error") {
        loc = doc.locate(index)
        label = "PCRE substitution error"
        err = error
    }

    private class PerlEmit(override val start: Int, override val end: Int, val value: String) : EmitQueue.Entry {
        override fun apply(builder: StringBuilder, offset: Int): Int {
            builder.replace(start + offset, end + offset, value)
            return value.length - (end - start)
        }
    }
}