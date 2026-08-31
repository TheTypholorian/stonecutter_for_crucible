package dev.kikugie.stitcher.document.edit

import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.DocumentReference
import java.util.*

@Suppress("NOTHING_TO_INLINE")
class ModificationTracker(val document: Document, val start: Int, val end: Int) {
    constructor(reference: DocumentReference) : this(reference.document, reference.start, reference.end)

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

    /**
     * Records a replacement of the range [startIndex]..<[endIndex] with [text].
     *
     * Indices are relative to the [document] and confined to the tracked [start]..[end] window.
     * [end] is inclusive: passing it as [startIndex] appends at the end of the window.
     */
    fun replace(startIndex: Int, endIndex: Int, text: String): Boolean {
        require(isLive) { "Modification tracker is frozen" }
        require(startIndex in start..endIndex && endIndex in 0..end) { "Invalid range $startIndex..<$endIndex" }
        return eventList.add(ModificationEvent(startIndex, endIndex, text))
    }

    internal fun toList(): LinkedList<ModificationEvent> =
        eventList.entries.also { isLive = false }
}