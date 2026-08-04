package dev.kikugie.stitcher.replacement

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.document.text

interface ReplacementExecutor {
    val identifiers: Set<String>

    fun isEmpty(): Boolean

    fun collect(tracker: ModificationTracker, text: String, queue: EmitQueue)

    fun replace(tracker: ModificationTracker): Boolean {
        if (isEmpty())
            return false

        val queue = EmitQueue()
        collect(tracker, tracker.text(), queue)

        if (queue.entries.isEmpty())
            return false

        for (emit in queue.entries)
            emit.applyTo(tracker)
        return true
    }
}
