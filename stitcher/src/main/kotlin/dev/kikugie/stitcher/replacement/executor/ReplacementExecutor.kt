package dev.kikugie.stitcher.replacement.executor

import dev.kikugie.stitcher.replacement.RegexReplacement
import dev.kikugie.stitcher.replacement.Replacement
import dev.kikugie.stitcher.replacement.StringReplacement
import dev.kikugie.stitcher.replacement.search.EmitConsumer
import dev.kikugie.stitcher.replacement.search.NativeSearcher
import dev.kikugie.stitcher.replacement.search.RegexSearcher
import dev.kikugie.stitcher.replacement.search.SearchData
import dev.kikugie.stitcher.replacement.search.SearchEmit
import dev.kikugie.stitcher.replacement.search.SearchEmitQueue
import dev.kikugie.stitcher.replacement.search.TrieSearcher
import dev.kikugie.stitcher.util.buildString

interface ReplacementExecutor {
    fun update(enabled: Collection<String> = emptySet(), disabled: Collection<String> = emptySet())
    fun collect(text: CharSequence, queue: SearchEmitQueue<SearchData>)

    fun replace(text: String): String {
        val queue = SearchEmitQueue<SearchData>().also { collect(text, it) }
        return if (queue.isEmpty()) text else buildString(text) {
            var offset = 0
            for (emit in queue)
                offset += emit.apply(this, offset)
        }
    }
}

object EmptyReplacementExecutor : ReplacementExecutor {
    override fun update(enabled: Collection<String>, disabled: Collection<String>): Unit = Unit
    override fun collect(text: CharSequence, queue: SearchEmitQueue<SearchData>): Unit = Unit

    override fun replace(text: String): String = text
}

class CompositeReplacementExecutor(replacements: List<Replacement>) : ReplacementExecutor {
    private val identifiers: MutableSet<String> = mutableSetOf<String>().apply {
        replacements.mapNotNullTo(this) { it.identifier?.takeIf { _ -> Replacement.Tag.DEFAULT_ENABLED in it.tags } }
    }
    private val stringExecutor: StringReplacementExecutor
    private val regexExecutor: RegexReplacementExecutor
    override fun update(enabled: Collection<String>, disabled: Collection<String>) {
        identifiers += enabled
        identifiers -= disabled
    }

    override fun collect(text: CharSequence, queue: SearchEmitQueue<SearchData>) {
        stringExecutor.collect(text, queue)
        regexExecutor.collect(text, queue)
    }

    init {
        val replacements = replacements.asSequence()
        stringExecutor = StringReplacementExecutor(replacements.filterIsInstance<StringReplacement>().asIterable(), identifiers)
        regexExecutor = RegexReplacementExecutor(replacements.filterIsInstance<RegexReplacement>().asIterable(), identifiers)
    }
}

class RegexReplacementExecutor(replacements: Iterable<RegexReplacement>, private val identifiers: MutableSet<String> = mutableSetOf()) : ReplacementExecutor {
    private val searcher = RegexSearcher<State>(replacements.toStateList())
    override fun update(enabled: Collection<String>, disabled: Collection<String>) {
        identifiers += enabled
        identifiers -= disabled
    }

    override fun collect(text: CharSequence, queue: SearchEmitQueue<SearchData>) {
        searcher.search(text, Consumer(queue))
    }

    private fun Iterable<RegexReplacement>.toStateList() = mutableListOf<RegexSearcher.Payload<State>>().apply {
        for (repl in this@toStateList)
            this += RegexSearcher.Payload(repl.regex, State(repl.target, repl.identifier))
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

class StringReplacementExecutor(replacements: Iterable<StringReplacement>, private val identifiers: MutableSet<String> = mutableSetOf()) : ReplacementExecutor {
    private val searcher = TrieSearcher<State>(replacements.toStateList())
    override fun update(enabled: Collection<String>, disabled: Collection<String>) {
        identifiers += enabled
        identifiers -= disabled
    }

    override fun collect(text: CharSequence, queue: SearchEmitQueue<SearchData>) {
        searcher.search(text, Consumer(queue))
    }

    private fun Iterable<StringReplacement>.toStateList() = buildList {
        for (repl in this@toStateList) {
            val state = State(repl.target, repl.identifier)
            if (repl.target !in repl.sources) this += TrieSearcher.Payload(repl.target, state)
            for (source in repl.sources) this += TrieSearcher.Payload(source, state)
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

class SimpleReplacementExecutor() : ReplacementExecutor {
    private val searcher = NativeSearcher<State>()
    override fun update(enabled: Collection<String>, disabled: Collection<String>) = Unit

    override fun collect(text: CharSequence, queue: SearchEmitQueue<SearchData>) {
        searcher.search(text) { @Suppress("UNCHECKED_CAST") queue.add(it as SearchEmit<SearchData>) }
    }

    fun push(keyword: String, value: String) {
        searcher.payloads += NativeSearcher.Payload(keyword, State(value))
    }

    fun pop() {
        searcher.payloads.removeLast()
    }

    @JvmInline
    private value class State(override val value: String) : SearchData
}
