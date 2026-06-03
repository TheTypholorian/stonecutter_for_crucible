package dev.kikugie.stitcher.replacement

import dev.kikugie.commons.collections.present
import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.ProblemID
import dev.kikugie.stitcher.issue.report
import org.pcre4j.Pcre2MatchData
import org.pcre4j.option.Pcre2SubstituteOption.EXTENDED
import org.pcre4j.option.Pcre2SubstituteOption.REPLACEMENT_ONLY
import org.pcre4j.regex.appendMatch
import java.util.*
import java.util.regex.MatchResult

private val REPLACEMENT_GROUP = ProblemID("replacement-execution")

internal class ReplacementExecutor(replacements: List<Replacement>, problems: FileProblemReporter) {
    private val identifiers: MutableSet<String> = mutableSetOf()
    private val stringExecutor: StringExecutor = StringExecutor(replacements, problems)
    private val regexExecutor: RegexExecutor = RegexExecutor(replacements)
    private val perlExecutor: PerlExecutor = PerlExecutor(replacements)
    private val Replacement.active: Boolean
        get() = identifier == null || identifier in identifiers

    init {
        for (repl in replacements) if (repl.identifier != null && repl.enabled)
            identifiers += repl.identifier!!
    }

    fun toggle(enable: Set<String> = emptySet(), disable: Set<String> = emptySet()) {
        identifiers += enable
        identifiers -= disable
        stringExecutor.reset()
    }

    fun include(new: Iterable<StringReplacement>) {
        stringExecutor.include(new)
    }

    fun collect(offset: Int, text: CharSequence, queue: EmitQueue) {
        stringExecutor.collect(offset, text, queue)
        regexExecutor.collect(text, queue)
        perlExecutor.collect(text, queue)
    }

    fun replace(offset: Int, builder: StringBuilder) {
        val queue = EmitQueue()
        collect(offset, builder, queue)

        var offset = 0
        for (emit in queue.entries)
            offset += emit.apply(builder, offset)
    }

    private inner class StringExecutor(replacements: List<Replacement>, private val problems: FileProblemReporter) {
        private val replacements = mutableMapOf<String, MutableList<StringReplacement>>()
            .apply { replacements.byPattern(this) }
        private val searcher = AhoCorasick(replacements.toPayloads())
        private val cache = mutableMapOf<String, String?>()

        fun reset() {
            cache.clear()
        }

        fun include(new: Iterable<StringReplacement>) {
            new.byPattern(replacements)
            searcher += new.toPayloads()
            cache.clear()
        }

        fun collect(start: Int, text: CharSequence, queue: EmitQueue) {
            for (match in searcher.search(text))
                if (replacements[match.pattern]?.any { it.active } ?: true)
                    queue += StringEmit(start, match)
        }

        private fun Iterable<Replacement>.byPattern(map: MutableMap<String, MutableList<StringReplacement>>) {
            for (repl in this) if (repl is StringReplacement)
                map.getOrPut(repl.pattern, ::mutableListOf) += repl
        }

        private fun Iterable<Replacement>.toPayloads() = buildSet {
            for (entry in this@toPayloads) if (entry is StringReplacement) {
                this += entry.pattern
                this += entry.target
            }
        }

        private fun replacementFor(start: Int, ctx: AhoCorasick.Match): String? =
            cache.getOrPut(ctx.pattern) { replacementFor(start, ctx, ctx.pattern) }

        private tailrec fun replacementFor(start: Int, ctx: AhoCorasick.Match, pattern: String): String? =
            when (val next = transitionFor(start, ctx, pattern)) {
                pattern, ctx.pattern -> next
                null -> pattern
                else -> replacementFor(start, ctx, next)
            }

        private fun transitionFor(start: Int, ctx: AhoCorasick.Match, pattern: String): String? {
            val variants = replacements[pattern]?.takeIf(List<*>::isNotEmpty)
                ?: return null

            val candidates = buildSet {
                for (it in variants) if (it.active)
                    this += it.target
            }

            if (candidates.size > 1) reportAmbiguousTransition(start, ctx, pattern, candidates)
            return candidates.singleOrNull()
        }

        private fun reportAmbiguousTransition(
            start: Int,
            ctx: AhoCorasick.Match,
            pattern: String,
            candidates: Set<String>
        ) = problems.report(REPLACEMENT_GROUP.child("ambiguous-transition"), start + ctx.start) {
            label = "Ambiguous replacement"
            details = buildString {
                append("Pattern ${ctx.pattern}")
                if (ctx.pattern != pattern) append(" (through $pattern)")
                appendLine(" can be replaced with multiple values:")
                append(candidates.present { "'$it'" })
            }
        }

        private inner class StringEmit(val offset: Int, val match: AhoCorasick.Match) : EmitQueue.Entry {
            override val start: Int get() = match.start
            override val end: Int get() = match.end
            private val value: String? by lazy { replacementFor(offset, match) }

            override fun apply(builder: StringBuilder, offset: Int): Int {
                if (value == null) return 0

                builder.replace(start + offset, end + offset, value)
                return value!!.length - (end - start)
            }
        }
    }

    private inner class RegexExecutor(replacements: List<Replacement>) {
        private val replacements = replacements.filterIsInstance<RegexReplacement>()

        fun collect(text: CharSequence, queue: EmitQueue) {
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

        private inner class RegexEmit(val match: MatchResult, val value: String) : EmitQueue.Entry {
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

    private inner class PerlExecutor(replacements: List<Replacement>) {
        private val replacements = replacements.filterIsInstance<PerlReplacement>()

        fun collect(text: CharSequence, queue: EmitQueue) {
            val string = text.toString()
            for (repl in replacements) if (repl.active)
                process(repl, string, queue)
        }

        private fun process(repl: PerlReplacement, text: String, queue: EmitQueue) {
            val data = Pcre2MatchData(repl.regex)
            var start = 0
            while (start in text.indices) {
                val value = repl.regex.substitute(text, 0, EnumSet.of(EXTENDED, REPLACEMENT_ONLY), data, null, repl.target)
                if (value.isEmpty()) break

                val offsets = data.ovector()
                queue += PerlEmit(offsets[0].toInt(), offsets[1].toInt(), value)
                start = offsets.first().toInt()
            }
        }

        private inner class PerlEmit(override val start: Int, override val end: Int, val value: String) : EmitQueue.Entry {
            override fun apply(builder: StringBuilder, offset: Int): Int {
                builder.replace(start + offset, end + offset, value)
                return value.length - (end - start)
            }
        }
    }
}
