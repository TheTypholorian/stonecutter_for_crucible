package dev.kikugie.stitcher.replacement

import dev.kikugie.stitcher.document.edit.ModificationTracker
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

    override fun isEmpty(): Boolean =
        replacements.isEmpty()

    override fun collect(tracker: ModificationTracker, text: String, queue: EmitQueue) {
        if (replacements.isEmpty()) return
        for (match in searcher.search(text)) {
            queue += StringEmit(match, findReplacement(match) ?: continue)
        }
    }

    private fun findReplacement(match: AhoCorasickTrie.Match): String? {
        TODO("Not yet implemented")
    }

    private class StringEmit(val match: AhoCorasickTrie.Match, val value: String) : EmitQueue.Entry {
        override val start: Int get() = match.start
        override val end: Int get() = match.end

        override fun applyTo(tracker: ModificationTracker) {
            tracker.replace(tracker.start + start, tracker.start + end, value)
        }
    }
}

private fun Sequence<StringReplacement>.collectTo(map: MutableMap<String, MutableList<StringReplacement>>): Unit = forEach {
    map.getOrPut(it.pattern, ::mutableListOf) += it
    map.getOrPut(it.target, ::mutableListOf) += it
}

private fun Sequence<StringReplacement>.toPayloads(): Sequence<String> =
    flatMap { (pattern, target) -> listOf(pattern, target) }
