package dev.kikugie.stitcher.replacement

import java.util.LinkedList
import kotlin.collections.iterator
import kotlin.text.iterator

internal data class TriePayload<T : Any>(val lookup: String, val data: T)
internal data class TrieEmit<T : Any>(val data: T, val start: Int, val end: Int) {
    val length: Int inline get() = end - start
}

private class TrieState<T : Any> {
    val outputs: MutableList<TriePayload<T>> = mutableListOf()
    val goto: MutableMap<Char, TrieState<T>> = mutableMapOf()
    lateinit var fail: TrieState<T>
}

internal class Trie<T : Any>(private val payloads: Iterable<TriePayload<T>>) {
    private val root = TrieState<T>().apply { fail = this }

    init {
        buildGotoFunctions()
        buildFailFunctions()
    }

    fun search(text: CharSequence, handler: (TrieEmit<T>) -> Unit) {
        var current = root
        for ((i, char) in text.withIndex()) {
            current = nextState(current, char)
            processEmits(i, current.outputs, handler)
        }
    }

    private fun buildGotoFunctions() {
        for (payload in payloads) {
            var current = root
            for (char in payload.lookup)
                current = current.goto.getOrPut(char, ::TrieState)

            current.outputs += payload
        }
    }

    private fun buildFailFunctions() {
        val queue = LinkedList(root.goto.values)
        for (state in root.goto.values)
            state.fail = root

        while (queue.isNotEmpty()) {
            val current = queue.remove()

            for ((char, next) in current.goto) {
                queue.add(next)

                var state = current.fail
                while (state != root && state.goto[char] == null)
                    state = state.fail

                next.fail = state.goto[char] ?: root
                next.outputs += next.fail.outputs
            }
        }
    }

    private fun processEmits(index: Int, payloads: List<TriePayload<T>>, handler: (TrieEmit<T>) -> Unit) {
        for (payload in payloads) TrieEmit(payload.data, index - payload.lookup.length + 1, index + 1).let(handler)
    }

    private tailrec fun nextState(state: TrieState<T>, char: Char): TrieState<T> =
        state.goto[char] ?: if (state === root) root else nextState(state.fail, char)
}

@JvmInline
internal value class TrieEmitList<T : Any>(private val list: LinkedList<TrieEmit<T>> = LinkedList()) : Iterable<TrieEmit<T>> {
    override fun iterator(): Iterator<TrieEmit<T>> = list.iterator()

    operator fun plusAssign(emit: TrieEmit<T>) {
        add(emit)
    }

    fun add(emit: TrieEmit<T>): Boolean =
        if (list.isEmpty() || emit.end >= list.last().end) offer(emit) else insert(emit)

    private fun offer(emit: TrieEmit<T>): Boolean {
        while (list.isNotEmpty()) {
            val last = list.last()
            when {
                last.start == emit.start ->
                    if (last.length > emit.length) return false
                    else list.removeLast()

                last.start < emit.start ->
                    if (last.end > emit.start) return false
                    else break

                else -> list.removeLast()
            }
        }
        return list.offer(emit)
    }

    private fun insert(emit: TrieEmit<T>): Boolean {
        val iterator = list.listIterator()
        for (existing in iterator) when {
            existing.start < emit.start ->
                if (existing.end > emit.start) return false

            existing.start == emit.start ->
                if (existing.length > emit.length) return false
                else iterator.remove()

            else ->
                if (emit.end > existing.start) iterator.remove()
                else {
                    iterator.previous()
                    iterator.add(emit)
                    return true
                }
        }
        iterator.add(emit)
        return true
    }
}
