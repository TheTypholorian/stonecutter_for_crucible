package dev.kikugie.stitcher.replacement

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.document.issue.ProblemReport.Severity
import dev.kikugie.stitcher.document.issue.REPLACEMENT_GROUP
import dev.kikugie.stitcher.document.issue.report
import dev.kikugie.stitcher.document.reference
import dev.kikugie.stonecutter.model.process.Replacement
import dev.kikugie.stonecutter.model.process.StringReplacement

class StringExecutor(replacements: List<Replacement>, override val identifiers: Set<String>) : ReplacementExecutor {
    private val index: MutableMap<String, MutableList<StringReplacement>> = HashMap()
    private val cache: MutableMap<String, String?> = HashMap()
    private val searcher: AhoCorasickTrie = AhoCorasickTrie()

    init {
        this += replacements.asSequence().filterIsInstance<StringReplacement>()
    }

    fun clear(): Unit = cache.clear()

    operator fun plusAssign(new: Sequence<StringReplacement>) {
        new.collectTo(index)
        searcher += new.toPayloads()
        clear()
    }

    override fun isEmpty(): Boolean =
        index.isEmpty()

    override fun collect(tracker: ModificationTracker, text: String, queue: EmitQueue) {
        if (index.isEmpty()) return
        for (match in searcher.search(text))
            queue += StringEmit(match, findReplacement(tracker, match) ?: continue)
    }

    private fun findReplacement(tracker: ModificationTracker, match: AhoCorasickTrie.Match): String? =
        cache.getOrPut(match.pattern) { resolveReplacement(tracker, match) }

    private fun resolveReplacement(tracker: ModificationTracker, match: AhoCorasickTrie.Match): String? {
        val start: String = match.pattern
        // No direct transition
        if (index[start].run { isNullOrEmpty() || none { it.isActive } })
            return null

        // Terminal outcomes reachable from `start`; loopback/circular branches contribute none.
        val outcomes = LinkedHashSet<String>().apply {
            walk(start, ArrayDeque<String>().apply { add(start) }, hashSetOf(start), this)
            this -= start
        }

        return when (outcomes.size) {
            0 -> start
            1 -> outcomes.first()
            else -> outcomes.first().also { tracker.reportAmbiguousReplacement(match, index.values, outcomes) }
        }
    }

    private tailrec fun walk(start: String, stack: ArrayDeque<String>, visited: MutableSet<String>, outcomes: MutableSet<String>) {
        val node = stack.removeFirstOrNull() ?: return
        val targets = index[node]?.filter { it.isActive && it.pattern == node }
        if (targets.isNullOrEmpty()) outcomes += node
        else for ((_, target) in targets)
            if (target != start && visited.add(target)) stack.addLast(target)
        return walk(start, stack, visited, outcomes)
    }

    private class StringEmit(val match: AhoCorasickTrie.Match, val value: String) : EmitQueue.Entry {
        override val start: Int get() = match.start
        override val end: Int get() = match.end

        override fun applyTo(tracker: ModificationTracker) {
            if (value != match.pattern) tracker.replace(tracker.start + start, tracker.start + end, value)
        }
    }
}

private fun Sequence<StringReplacement>.collectTo(map: MutableMap<String, MutableList<StringReplacement>>): Unit = forEach {
    map.getOrPut(it.pattern, ::mutableListOf) += it
    if (it.target != it.pattern) map.getOrPut(it.target, ::mutableListOf) += it
}

private fun Sequence<StringReplacement>.toPayloads(): Sequence<String> =
    flatMap { (pattern, target) -> listOf(pattern, target) }

private fun ModificationTracker.reportAmbiguousReplacement(
    match: AhoCorasickTrie.Match, replacements: Collection<Collection<StringReplacement>>, outcomes: Set<String>
): Unit = document.reference(start + match.start, start + match.end).report(REPLACEMENT_GROUP + "ambiguous-replacement") {
    label = "Ambiguous replacement"
    info = "'${match.pattern}' resolves to multiple targets: [${outcomes.joinToString { "'$it'" }}]"

    val ids = replacements.asSequence()
        .flatMap { it.mapNotNull(StringReplacement::identifier) }
        .filterNot { it.startsWith("%synthetic%") }
        .toSet()
    if (ids.isNotEmpty()) hint = "Active string replacements: [${ids.joinToString()}]"
}
