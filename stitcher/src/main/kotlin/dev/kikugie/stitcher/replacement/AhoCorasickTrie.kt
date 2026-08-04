package dev.kikugie.stitcher.replacement

import java.util.LinkedList

@JvmInline
value class AhoCorasickTrie private constructor(private val root: State) {
    constructor() : this(State())

    init {
        root.fail = root
    }

    operator fun plusAssign(pattern: String) {
        if (add(pattern)) rebuildFailLinks()
    }

    operator fun plusAssign(patterns: Sequence<String>) {
        if (patterns.fold(false) { acc, pattern -> add(pattern) || acc })
            rebuildFailLinks()
    }

    fun search(text: CharSequence): Sequence<Match> = sequence {
        var state = root
        for ((i, char) in text.withIndex()) {
            state = nextState(state, char)
            for (match in state.outputs)
                yield(Match(i - match.length + 1, match))
        }
    }

    private fun add(pattern: String): Boolean {
        var current = root
        for (char in pattern)
            current = current.paths.getOrPut(char, ::State)

        return current.outputs.add(pattern)
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

    private tailrec fun nextState(state: State, char: Char): State =
        state.paths[char] ?: if (state === root) root else nextState(state.fail, char)

    class Match(val start: Int, val pattern: String) {
        val end: Int inline get() = start + pattern.length
    }

    private class State {
        val outputs: MutableSet<String> = HashSet()
        val paths: MutableMap<Char, State> = HashMap()
        lateinit var fail: State
    }
}