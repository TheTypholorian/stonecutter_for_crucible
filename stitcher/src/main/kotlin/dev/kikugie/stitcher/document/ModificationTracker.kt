package dev.kikugie.stitcher.document

import dev.kikugie.stitcher.util.NonOverlappingList
import java.util.LinkedList
import kotlin.math.max
import kotlin.math.min

class ModificationTracker {
    data class Event(override val start: Int, override val end: Int, val text: String) : NonOverlappingList.Entry {
        val textLength: Int inline get() = text.length
    }
    val modifications: List<Event> field = EventList()
    var modified: UInt = 0u
        private set

    fun insert(index: Int, text: String): Boolean =
        modifications.add(Event(index, index, text)).also { if (it) modified++ }

    fun delete(start: Int, end: Int): Boolean =
        modifications.add(Event(start, end, "")).also { if (it) modified++ }

    fun replace(start: Int, end: Int, text: String): Boolean =
        modifications.add(Event(start, end, text)).also { if (it) modified++ }
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
