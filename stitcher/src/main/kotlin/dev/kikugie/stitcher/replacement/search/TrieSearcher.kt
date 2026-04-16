package dev.kikugie.stitcher.replacement.search

import java.util.LinkedList

/**
 * An efficient pattern searcher that uses the Aho-Corasick algorithm to find multiple keywords simultaneously.
 *
 * Builds a trie structure with failure links to enable finding all occurrences of registered keywords in linear time,
 * including overlapping matches. However, the patterns cannot be removed — for that use [NativeSearcher].
 */
@JvmInline
value class TrieSearcher<T> private constructor(private val root: TrieState<T>): PatternSearcher<TrieSearcher.Context, T> {
    constructor(initial: Iterable<Payload<T>> = emptyList()) : this(TrieState<T>()) { plusAssign(initial) }

    init {
        root.fail = root
    }

    operator fun plusAssign(payload: Payload<T>) {
        if (insertPayload(payload))
            rebuildFailLinks()
    }

    operator fun plusAssign(payloads: Iterable<Payload<T>>) {
        if (payloads.fold(false) { bl, it -> insertPayload(it) || bl })
            rebuildFailLinks()
    }

    override fun search(text: CharSequence, consumer: EmitConsumer<Context, T>) {
        var state = root
        for ((i, char) in text.withIndex()) {
            state = nextState(state, char)
            processEmits(i, state, consumer)
        }
    }

    private fun insertPayload(payload: Payload<T>): Boolean {
        var current = root
        for (char in payload.keyword)
            current = current.paths.getOrPut(char, ::TrieState)

        return current.outputs.add(payload)
    }

    private fun rebuildFailLinks() {
        val queue = LinkedList(root.paths.values)
        for (state in root.paths.values)
            state.fail = root

        while (queue.isNotEmpty()) {
            val current = queue.remove()

            for ((char, next) in current.paths) {
                queue.add(next)

                var state = current.fail
                while (state != root && state.paths[char] == null)
                    state = state.fail

                next.fail = state.paths[char] ?: root
                next.outputs += next.fail.outputs
            }
        }
    }

    private fun processEmits(index: Int, state: TrieState<T>, handler: EmitConsumer<Context, T>) {
        for ((pattern, data) in state.outputs)
            handler.accept(Context(index - pattern.length + 1, index + 1, pattern), data)
    }

    private tailrec fun nextState(state: TrieState<T>, char: Char): TrieState<T> =
        state.paths[char] ?: if (state === root) root else nextState(state.fail, char)

    data class Context(override val start: Int, override val end: Int, val pattern: String) : EmitContext

    data class Payload<T>(val keyword: String, val data: T)

    private class TrieState<T> {
        val outputs: MutableSet<Payload<T>> = mutableSetOf()
        val paths: MutableMap<Char, TrieState<T>> = mutableMapOf()
        lateinit var fail: TrieState<T>

    }
}
