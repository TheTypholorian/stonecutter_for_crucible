package dev.kikugie.stitcher.replacement

internal interface ReplacementExecutor {
    fun update(enabled: Set<String> = emptySet(), disabled: Set<String> = emptySet())
    fun replace(builder: StringBuilder)

    companion object {
        operator fun invoke(replacements: List<Replacement>): ReplacementExecutor =
            if (replacements.isEmpty()) EmptyReplacementExecutor
            else CompositeReplacementExecutor(replacements)
    }
}

private object EmptyReplacementExecutor : ReplacementExecutor {
    override fun update(enabled: Set<String>, disabled: Set<String>) = Unit
    override fun replace(builder: StringBuilder) = Unit
}

private class CompositeReplacementExecutor(replacements: List<Replacement>) : ReplacementExecutor {
    private val stringReplacements: Trie<ReplacementState> = buildTrie(replacements)
    private val regexReplacements: List<RegexReplacement> = replacements.filterIsInstance<RegexReplacement>()
    private val enabledIdentifiers = replacements
        .mapNotNull { entry -> entry.identifier.takeIf { Replacement.Tag.DEFAULT_ENABLED in entry.tags } }.toMutableSet()

    override fun update(enabled: Set<String>, disabled: Set<String>) {
        enabledIdentifiers += enabled
        enabledIdentifiers -= disabled
    }

    override fun replace(builder: StringBuilder) {
        val collector = EmitCollector()

        stringReplacements.search(builder, collector)
        collectRegex(builder, collector)
        apply(builder, collector.list)
    }

    private fun collectRegex(builder: StringBuilder, collector: EmitCollector) = regexReplacements.forEach { entry ->
        if (!entry.identifier.isAccepted()) return@forEach

        for (match in entry.regex.findAll(builder)) {
            val state = RegexReplacementState(entry.identifier, match, entry.target)
            collector.acceptUnchecked(state, match.range)
        }
    }

    private fun apply(builder: StringBuilder, entries: Iterable<TrieEmit<ReplacementState>>) {
        var offset = 0
        for ((state, start, end) in entries)
            offset += state.apply(builder, start + offset, end + offset)
    }

    private fun String?.isAccepted(): Boolean = this == null || this in enabledIdentifiers

    inner class EmitCollector : (TrieEmit<ReplacementState>) -> Unit {
        val list = TrieEmitList<ReplacementState>()

        override fun invoke(emit: TrieEmit<ReplacementState>) {
            if (emit.data.identifier.isAccepted()) list += emit
        }

        fun acceptUnchecked(state: ReplacementState, range: IntRange) {
            list += TrieEmit(state, range.first, range.last + 1)
        }
    }
}

private interface ReplacementState {
    val identifier: String?
    fun apply(builder: StringBuilder, start: Int, end: Int): Int
}

private class StringReplacementState(override val identifier: String?, val value: String) : ReplacementState {
    constructor(replacement: StringReplacement) : this(replacement.identifier, replacement.target)
    override fun apply(builder: StringBuilder, start: Int, end: Int): Int {
        builder.replace(start, end, value)
        return value.length - (end - start)
    }
}

private class RegexReplacementState(override val identifier: String?, match: MatchResult, value: String) : ReplacementState {
    val value by lazy { process(match, value) }

    override fun apply(builder: StringBuilder, start: Int, end: Int): Int {
        builder.replace(start, end, value)
        return value.length - (end - start)
    }

    private fun process(match: MatchResult, value: String): String = buildString(value.length) {
        append(value)
        var offset = 0
        val groups = match.groups
        for (place in value.findInsertionPlaces())
            offset += place.apply(this, offset) { groups[it]?.value }
    }
}

private fun buildTrie(replacements: List<Replacement>): Trie<ReplacementState> = Trie(sequence {
    for (entry in replacements) if (entry is StringReplacement) {
        if (entry.target !in entry.sources)
            yield(TriePayload(entry.target, StringReplacementState(entry) as ReplacementState))
        for (source in entry.sources)
            yield(TriePayload(source, StringReplacementState(entry) as ReplacementState))
    }
}.asIterable())
