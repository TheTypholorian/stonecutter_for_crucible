package dev.kikugie.stitcher.document.edit

import dev.kikugie.stitcher.document.Document
import java.util.LinkedList

@Suppress("NOTHING_TO_INLINE")
class ModificationTracker(val document: Document) {
    private val eventList: ModificationList = ModificationList()
    private var isLive: Boolean = true

    inline fun insert(index: Int, text: String): Boolean =
        replace(index, index, text)

    inline fun delete(range: OpenEndRange<Int>): Boolean =
        replace(range.start, range.endExclusive, "")

    inline fun delete(start: Int, end: Int): Boolean =
        replace(start, end, "")

    inline fun replace(range: OpenEndRange<Int>, text: String): Boolean =
        replace(range.start, range.endExclusive, text)

    fun replace(startIndex: Int, endIndex: Int, text: String): Boolean {
        require(isLive) { "Modification tracker is frozen" }
        require(startIndex in 0..endIndex && endIndex in 0..document.length) { "Invalid range $startIndex..<$endIndex" }
        return eventList.add(ModificationEvent(startIndex, endIndex, text))
    }

    internal fun toList(): LinkedList<ModificationEvent> =
        eventList.entries.also { isLive = false }
}