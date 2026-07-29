package dev.kikugie.stitcher.replacement

import dev.kikugie.stonecutter.model.process.Replacement
import dev.kikugie.stonecutter.model.process.StringReplacement

class StringExecutor(replacements: List<Replacement>, override val identifiers: Set<String>) : ReplacementExecutor {
    private val replacements: MutableMap<String, MutableList<StringReplacement>> = HashMap()
    private val cache: MutableMap<String, String?> = HashMap()
    private val searcher: AhoCorasickTrie = AhoCorasickTrie()

    init {
        this += replacements.asSequence().filterIsInstance<StringReplacement>()
    }

    fun clear(): Unit = cache.clear()

    operator fun plusAssign(new: Sequence<StringReplacement>) {
        new.collectTo(replacements)
        searcher += new.toPayloads()
        clear()
    }

    override fun collect(text: CharSequence, queue: EmitQueue) {
        for (match in searcher.search(text)) {
            val value: String = findReplacement(match) ?: continue
            queue += StringEmit(match, value)
        }
    }

    private fun findReplacement(match: AhoCorasickTrie.Match): String? {
        TODO("Not yet implemented")
    }

    private class StringEmit(val match: AhoCorasickTrie.Match, val value: String) : EmitQueue.Entry {
        override val start: Int get() = match.start
        override val end: Int get() = match.end

        override fun appendTo(builder: Appendable) {
            builder.append(value)
        }
    }
}

private fun Sequence<StringReplacement>.collectTo(map: MutableMap<String, MutableList<StringReplacement>>) {
    for (repl in this) {
        map.getOrPut(repl.pattern, ::mutableListOf) += repl
        map.getOrPut(repl.target, ::mutableListOf) += repl
    }
}

private fun Sequence<StringReplacement>.toPayloads(): Sequence<String> = sequence {
    for ((pattern, target) in this@toPayloads) {
        yield(pattern)
        yield(target)
    }
}
