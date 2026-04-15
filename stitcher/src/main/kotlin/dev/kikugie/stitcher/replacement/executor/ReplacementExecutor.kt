package dev.kikugie.stitcher.replacement.executor

import dev.kikugie.stitcher.replacement.RegexReplacement
import dev.kikugie.stitcher.replacement.Replacement
import dev.kikugie.stitcher.replacement.StringReplacement
import dev.kikugie.stitcher.replacement.search.*

interface ReplacementExecutor {
    fun update(enabled: Collection<String> = emptySet(), disabled: Collection<String> = emptySet()) {}
    fun collect(text: CharSequence, queue: SearchEmitQueue<SearchData>)
}

fun ReplacementExecutor.replaceIn(builder: StringBuilder) {
    val queue = SearchEmitQueue<SearchData>()
        .also { collect(builder, it) }

    var offset = 0
    for (emit in queue)
        offset += emit.apply(builder, offset)
}

class CompositeReplacementExecutor(replacements: List<Replacement>) : ReplacementExecutor {
    private val identifiers: MutableSet<String> = mutableSetOf<String>().apply {
        for (it in replacements) if (it.identifier != null && Replacement.Tag.DEFAULT_ENABLED in it.tags)
            this += it.identifier!!
    }

    private val stringExecutor: StringReplacementExecutor?
    private val regexExecutor: RegexReplacementExecutor?

    override fun update(enabled: Collection<String>, disabled: Collection<String>) {
        if (stringExecutor == null || regexExecutor == null) return
        identifiers += enabled
        identifiers -= disabled
    }

    override fun collect(text: CharSequence, queue: SearchEmitQueue<SearchData>) {
        stringExecutor?.collect(text, queue)
        regexExecutor?.collect(text, queue)
    }

    init {
        if (replacements.isEmpty()) {
            stringExecutor = null
            regexExecutor = null
        } else {
            val replacements = replacements.asSequence()
            stringExecutor = StringReplacementExecutor(replacements.filterIsInstance<StringReplacement>().asIterable(), identifiers)
            regexExecutor = RegexReplacementExecutor(replacements.filterIsInstance<RegexReplacement>().asIterable(), identifiers)
        }
    }
}

private class RegexReplacementExecutor(replacements: Iterable<RegexReplacement>, private val identifiers: Set<String>) : ReplacementExecutor {
    private val searcher: RegexSearcher<State> = RegexSearcher(replacements.map { RegexSearcher.Payload(it.regex, State(it.target, it.identifier)) })

    override fun collect(text: CharSequence, queue: SearchEmitQueue<SearchData>) {
        searcher.search(text, Consumer(queue))
    }

    private class State(override val value: String, val id: String?) : SearchData

    private inner class Consumer(val queue: SearchEmitQueue<SearchData>) : EmitConsumer<State> {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(emit: SearchEmit<State>) {
            if (emit.data.id.let { it == null || it in identifiers })
                queue.add(emit as SearchEmit<SearchData>)
        }
    }
}

class StringReplacementExecutor(replacements: Iterable<StringReplacement>, private val identifiers: Set<String>) : ReplacementExecutor {
    private val searcher: TrieSearcher<State> = TrieSearcher(replacements.toStateList())

    override fun collect(text: CharSequence, queue: SearchEmitQueue<SearchData>) {
        searcher.search(text, Consumer(queue))
    }

    private fun Iterable<StringReplacement>.toStateList() = buildList {
        for (repl in this@toStateList) {
            val state = State(repl.target, repl.identifier)
            this += TrieSearcher.Payload(repl.pattern, state)
            if (repl.pattern != repl.target) this += TrieSearcher.Payload(repl.target, state)
        }
    }

    private class State(override val value: String, val id: String?) : SearchData

    private inner class Consumer(val queue: SearchEmitQueue<SearchData>) : EmitConsumer<State> {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(emit: SearchEmit<State>) {
            if (emit.data.id.let { it == null || it in identifiers })
                queue.add(emit as SearchEmit<SearchData>)
        }
    }
}
