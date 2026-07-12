@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.document

import dev.kikugie.stitcher.util.NonOverlappingList
import java.util.LinkedList
import kotlin.math.max
import kotlin.math.min

class ModificationTracker(val document: Document, val length: Int) {
    data class Event(override val start: Int, override val end: Int, val text: String) : NonOverlappingList.Entry {
        val textLength: Int inline get() = text.length
    }
    internal val changeList: List<Event> field = EventList()
    internal var timesModified: UInt = 0u
        private set

    inline fun insert(index: Int, text: String): Boolean =
        replace(index, index, text)

    inline fun delete(range: OpenEndRange<Int>): Boolean =
        replace(range.start, range.endExclusive, "")

    inline fun delete(start: Int, end: Int): Boolean =
        replace(start, end, "")

    inline fun replace(range: OpenEndRange<Int>, text: String): Boolean =
        replace(range.start, range.endExclusive, text)

    fun replace(start: Int, end: Int, text: String): Boolean {
        require(start in 0..<end && end in start..length) { "Text range $start..<$end !in 0..<${length + 1}" }
        return changeList.add(Event(start, end, text))
            .also { if (it) timesModified++ }
    }
}

private class EventList : NonOverlappingList<ModificationTracker.Event>(LinkedList()) {
    override fun onOverlap(prev: ModificationTracker.Event, next: ModificationTracker.Event): ModificationTracker.Event {
        val start = min(prev.start, next.start)
        val end = max(prev.end, next.end)
        val text = combineText(prev, next)
        return ModificationTracker.Event(start, end, text)
    }

    private fun combineText(prev: ModificationTracker.Event, next: ModificationTracker.Event): String = buildString {
        val diff = next.start - prev.start
        if (diff >= 0) {
            append(prev.text.take(diff))
            append(next.text)
        } else {
            append(next.text)
            append(prev.text.drop(-diff))
        }
    }
}
