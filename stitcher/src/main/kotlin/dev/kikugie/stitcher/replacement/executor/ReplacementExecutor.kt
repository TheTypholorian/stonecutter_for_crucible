package dev.kikugie.stitcher.replacement.executor

import dev.kikugie.commons.collections.present
import dev.kikugie.commons.then
import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.ProblemID
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.replacement.RegexReplacement
import dev.kikugie.stitcher.replacement.Replacement
import dev.kikugie.stitcher.replacement.StringReplacement
import dev.kikugie.stitcher.replacement.search.*

private val REPLACEMENT_GROUP = ProblemID("replacement-execution")

interface ReplacementExecutor {
    fun update(enabled: Collection<String> = emptySet(), disabled: Collection<String> = emptySet()) {}
    fun collect(text: CharSequence, queue: SearchEmitQueue)
}

fun ReplacementExecutor.replaceIn(builder: StringBuilder) {
    val queue = SearchEmitQueue()
        .also { collect(builder, it) }

    var offset = 0
    for (emit in queue)
        offset += emit.apply(builder, offset)
}

class CompositeReplacementExecutor(replacements: List<Replacement>, problems: FileProblemReporter) : ReplacementExecutor {
    private val identifiers: MutableSet<String> = mutableSetOf<String>().apply {
        for (it in replacements) if (it.identifier != null && Replacement.Tag.DEFAULT_ENABLED in it.tags)
            this += it.identifier!!
    }

    private val stringExecutor: StringReplacementExecutor
    private val regexExecutor: RegexReplacementExecutor

    override fun update(enabled: Collection<String>, disabled: Collection<String>) {
        identifiers += enabled
        identifiers -= disabled
    }

    override fun collect(text: CharSequence, queue: SearchEmitQueue) {
        stringExecutor.collect(text, queue)
        regexExecutor.collect(text, queue)
    }

    operator fun plusAssign(replacement: Replacement) = when(replacement) {
        is StringReplacement -> stringExecutor += replacement
        is RegexReplacement -> regexExecutor += replacement
    }

    init {
        val replacements = replacements.asSequence()
        stringExecutor = StringReplacementExecutor(replacements.filterIsInstance<StringReplacement>().asIterable(), identifiers, problems)
        regexExecutor = RegexReplacementExecutor(replacements.filterIsInstance<RegexReplacement>().asIterable(), identifiers)
    }
}

private class RegexReplacementExecutor(replacements: Iterable<RegexReplacement>, private val identifiers: Set<String>) : ReplacementExecutor {
    private val searcher: RegexSearcher<State> = RegexSearcher(replacements.toStateList())

    override fun collect(text: CharSequence, queue: SearchEmitQueue) {
        searcher.search(text, Consumer(queue))
    }

    operator fun plusAssign(replacement: RegexReplacement) {
        searcher.payloads += RegexSearcher.Payload(replacement.regex, State(replacement.target, replacement.identifier))
    }

    private fun Iterable<RegexReplacement>.toStateList(): MutableList<RegexSearcher.Payload<State>> = mutableListOf<RegexSearcher.Payload<State>>().apply {
        this@toStateList.mapTo(this) { RegexSearcher.Payload(it.regex, State(it.target, it.identifier)) }
    }

    private class State(val value: String, val id: String?)

    private class Entry(override val start: Int, override val end: Int, val value: String) : EmitEntry {
        override fun apply(builder: StringBuilder, offset: Int): Int {
            builder.replace(start + offset, end + offset, value)
            return value.length - this.length
        }
    }

    private inner class Consumer(val queue: SearchEmitQueue) : EmitConsumer<RegexSearcher.Context, State> {
        override fun accept(ctx: RegexSearcher.Context, emit: State) {
            if (emit.id == null || emit.id in identifiers)
                queue.add(Entry(ctx.start, ctx.end, emit.value))
        }
    }
}

class StringReplacementExecutor(
    replacements: Iterable<StringReplacement>,
    private val identifiers: Set<String>,
    private val problems: FileProblemReporter,
) : ReplacementExecutor {
    private val replacements: MutableMap<String, MutableList<StringReplacement>> = replacements.associateBySource()
    private val searcher: TrieSearcher<State> = TrieSearcher(replacements.toStateList())

    override fun collect(text: CharSequence, queue: SearchEmitQueue) {
        searcher.search(text, Consumer(queue))
    }

    operator fun plusAssign(replacement: StringReplacement) {
        searcher += buildList { replacement.toPayloads(this) }
    }

    private fun Iterable<StringReplacement>.associateBySource() = mutableMapOf<String, MutableList<StringReplacement>>().apply {
        for (repl in this@associateBySource) this.getOrPut(repl.pattern, ::mutableListOf) += repl
    }

    private fun Iterable<StringReplacement>.toStateList() = buildList {
        for (repl in this@toStateList) repl.toPayloads(this)
    }

    private fun StringReplacement.toPayloads(list: MutableList<TrieSearcher.Payload<State>>) {
        list += TrieSearcher.Payload(pattern, State(identifier))
        if (pattern != target)
            list += TrieSearcher.Payload(target, State(identifier))
    }

    private fun findReplacement(pattern: String): String? {
        var candidate: String = pattern
        while (true) when (val next = findCandidate(pattern, candidate)) {
            pattern if (candidate != pattern) -> return reportCyclicalTransition(pattern, candidate, next) then null
            candidate -> return candidate
            null -> return null
            else -> candidate = next
        }
    }

    private fun findCandidate(original: String, pattern: String): String? {
        val variants = replacements[pattern]?.takeIf { it.isNotEmpty() } ?: return null

        val candidates = mutableSetOf<String>()
        for (it in variants) {
            if (it.identifier != null && it.identifier !in identifiers) continue
            candidates += it.target
        }

        return if (candidates.size == 1) candidates.single()
        else reportAmbiguousTransition(original, pattern, candidates) then null
    }

    private fun reportAmbiguousTransition(
        start: String,
        pattern: String,
        candidates: Collection<String>
    ) = problems.report(REPLACEMENT_GROUP.child("ambiguous-transition")) {
        label = "Ambiguous replacement"
        details = buildString {
            append("Pattern $start")
            if (start != pattern) append(" (through $pattern)")
            appendLine(" can be replaced with multiple values:")
            append(candidates.present { "'$it'" })
        }
        // TODO: add docs links
    }

    private fun reportCyclicalTransition(
        start: String,
        pattern: String,
        candidate: String
    ) = problems.report(REPLACEMENT_GROUP.child("cyclical-transition")) {
        label = "Cyclical replacement"
    }

    @JvmInline
    private value class State(val id: String?)

    private inner class Entry(override val start: Int, override val end: Int, val pattern: String) : EmitEntry {
        private val value by lazy { findReplacement(pattern) }

        override fun apply(builder: StringBuilder, offset: Int): Int {
            if (value == null) return 0

            builder.replace(start + offset, end + offset, value)
            return value!!.length - this.length
        }
    }

    private inner class Consumer(val queue: SearchEmitQueue) : EmitConsumer<TrieSearcher.Context, State> {
        override fun accept(ctx: TrieSearcher.Context, emit: State) {
            if (emit.id == null || emit.id in identifiers)
                queue.add(Entry(ctx.start, ctx.end, ctx.pattern))
        }
    }
}
