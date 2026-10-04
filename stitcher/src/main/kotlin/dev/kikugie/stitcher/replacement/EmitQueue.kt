package dev.kikugie.stitcher.replacement

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.commons.ranges.OpenIntRange
import dev.kikugie.commons.ranges.length
import java.util.*

@JvmInline
value class EmitQueue(val entries: LinkedList<Entry> = LinkedList()) {
    @Suppress("NOTHING_TO_INLINE")
    inline operator fun plusAssign(entry: Entry) {
        add(entry)
    }

    fun add(entry: Entry): Boolean = when {
        entries.isEmpty() || entry.end >= entries.peekLast().end -> addFromEnd(entry)
        else -> addFromStart(entry)
    }

    private fun addFromStart(emit: Entry): Boolean {
        val iterator = entries.listIterator()
        for (existing in iterator) when {
            existing.start < emit.start ->
                if (existing.end > emit.start) return false

            existing.start == emit.start ->
                if (existing.length > emit.length) return false
                else iterator.remove()

            else ->
                if (emit.end > existing.start) iterator.remove()
                else {
                    iterator.previous()
                    iterator.add(emit)
                    return true
                }
        }
        iterator.add(emit)
        return true
    }

    private fun addFromEnd(emit: Entry): Boolean {
        while (entries.isNotEmpty()) {
            val last = entries.peekLast()
            when {
                last.start == emit.start ->
                    if (last.length > emit.length) return false
                    else entries.removeLast()

                last.start < emit.start ->
                    if (last.end > emit.start) return false
                    else break

                else -> entries.removeLast()
            }
        }
        return entries.offer(emit)
    }

    interface Entry : OpenIntRange {
        fun applyTo(tracker: ModificationTracker)
    }
}