package dev.kikugie.stitcher.replacement.search

import java.util.LinkedList

/**
 * An efficient pattern searcher that uses the Aho-Corasick algorithm to find multiple keywords simultaneously.
 *
 * Builds a trie structure with failure links to enable finding all occurrences of registered keywords in linear time,
 * including overlapping matches. However, the patterns cannot be removed — for that use [NativeSearcher].
 */
class TrieSearcher<T : SearchData>(payloads: Iterable<Payload<T>>) : PatternSearcher<T> {
    private val root = TrieState<T>().apply { fail = this }

    init {
        // Build successful paths
        for (payload in payloads) {
            var current = root
            for (char in payload.keyword)
                current = current.paths.getOrPut(char, ::TrieState)

            current.outputs += payload
        }

        // Build failure states
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

    override fun search(text: CharSequence, consumer: EmitConsumer<T>) {
        var state = root
        for ((i, char) in text.withIndex()) {
            state = nextState(state, char)
            processEmits(i, state.outputs, consumer)
        }
    }

    private fun processEmits(index: Int, payloads: List<Payload<T>>, handler: EmitConsumer<T>) {
        for (it in payloads)
            handler(TrieEmit(it.data, index - it.keyword.length + 1, index + 1))
    }

    private tailrec fun nextState(state: TrieState<T>, char: Char): TrieState<T> =
        state.paths[char] ?: if (state === root) root else nextState(state.fail, char)

    data class Payload<T>(val keyword: String, val data: T)

    private class TrieState<T> {
        val outputs: MutableList<Payload<T>> = mutableListOf()
        val paths: MutableMap<Char, TrieState<T>> = mutableMapOf()
        lateinit var fail: TrieState<T>
    }

    private data class TrieEmit<T : SearchData>(override val data: T, override val start: Int, override val end: Int) : SearchEmit<T> {
        override fun apply(builder: StringBuilder, offset: Int): Int {
            builder.replace(start + offset, end + offset, data.value)
            return data.value.length - this.length
        }
    }
}

