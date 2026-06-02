package dev.kikugie.stitcher.replacement

import dev.kikugie.commons.collections.present
import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.ProblemID
import dev.kikugie.stitcher.issue.report
import kotlin.apply
import kotlin.collections.plusAssign

private val REPLACEMENT_GROUP = ProblemID("replacement-execution")

internal class ReplacementExecutor(replacements: List<Replacement>, problems: FileProblemReporter) {
    private val identifiers: MutableSet<String> = mutableSetOf()
    private val stringExecutor: StringExecutor = StringExecutor(replacements, problems)
    private val regexExecutor: RegexExecutor = RegexExecutor(replacements)
    private val Replacement.enabled: Boolean
        get() = identifier == null || identifier in identifiers

    init {
        for (repl in replacements) if (repl.identifier != null && Replacement.Tag.DEFAULT_ENABLED in repl.tags)
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
                if (replacements[match.pattern].orEmpty().any { it.enabled })
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
                pattern, ctx.pattern, null -> next
                else -> replacementFor(start, ctx, next)
            }

        private fun transitionFor(start: Int, ctx: AhoCorasick.Match, pattern: String): String? {
            val variants = replacements[pattern]?.takeIf(List<*>::isNotEmpty)
                ?: return null

            val candidates = buildSet {
                for (it in variants) if (it.enabled)
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
            private val value by lazy { replacementFor(offset, match) }

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
            for (repl in replacements) if (repl.enabled)
                for (match in repl.regex.findAll(text))
                    queue += RegexEmit(match, repl.target)
        }

        private inner class RegexEmit(val match: MatchResult, private val value: String) : EmitQueue.Entry {
            override val start: Int get() = match.range.first
            override val end: Int get() = match.range.last + 1

            override fun apply(builder: StringBuilder, offset: Int): Int {
                builder.replace(start + offset, end + offset, value)
                return value.length - (end - start)
            }
        }
    }
}