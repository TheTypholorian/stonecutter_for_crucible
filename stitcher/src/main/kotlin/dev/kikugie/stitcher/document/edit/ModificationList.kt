package dev.kikugie.stitcher.document.edit

import java.util.*
import kotlin.math.max
import kotlin.math.min

private typealias Event = ModificationEvent

/**
 * Sorted non-overlapping list of [ModificationEvent]s.
 *
 * The implementation keeps edits events continuously sorted,
 * with adjacent and overlapping events being merged.
 * When modification events overlap, the newer value has priority.
 */
@JvmInline
value class ModificationList(val entries: LinkedList<Event> = LinkedList()) {

    /** Registers the [event], returning `true` if the list was modified as the result. */
    fun add(event: Event): Boolean = when {
        entries.isEmpty() || event.start > entries.last().end -> entries.addLast(event).let { true }
        event.end < entries.first().start -> entries.addFirst(event).let { true }
        else -> addMerging(event, entries.listIterator())
    }

    private fun addMerging(target: Event, iterator: MutableListIterator<Event>): Boolean {
        var target: Event = target
        for (event in iterator) {
            // This [event] is entirely before the [target] and can be skipped
            if (event.end < target.start)
                continue

            // The [event] is after the [target], and there were no prior overlaps
            if (event.start > target.end) {
                iterator.previous()
                break
            }

            target = merge(event, target)

            // The [target] couldn't be merged and can't be inserted
            if (target == event)
                return false

            iterator.remove()

            // If the [result] can't overlap with next entries, insert it; otherwise, continue merging
            if (target.end <= event.end) break
        }

        iterator.add(target)
        return true
    }

    private fun merge(prev: Event, next: Event): Event {
        if (prev == next) return prev

        if (prev.end == next.start) return Event(prev.start, next.end, prev.text + next.text)
        if (next.end == prev.start) return Event(next.start, prev.end, next.text + prev.text)

        val start = min(prev.start, next.start)
        val end = max(prev.end, next.end)
        val chars = CharArray(end - start)
        prev.text.toCharArray(chars, (prev.start - next.start).coerceAtLeast(0))
        next.text.toCharArray(chars, (next.start - prev.start).coerceAtLeast(0))
        return Event(start, end, String(chars))
    }
}
