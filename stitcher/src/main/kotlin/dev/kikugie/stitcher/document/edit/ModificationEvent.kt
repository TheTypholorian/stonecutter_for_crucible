package dev.kikugie.stitcher.document.edit

import dev.kikugie.stitcher.util.OpenIntRange

/**
 * Represents a change in the source [Document][dev.kikugie.stitcher.document.Document]
 * with the new value for the range [start]..<[end] being [text].
 */
data class ModificationEvent(override val start: Int, override val end: Int, val text: String) : OpenIntRange {
    /** Span of this event before the change is applied. */
    val sourceLength: Int inline get() = end - start

    /** Span of this event after the change is applied. */
    val textLength: Int inline get() = text.length

    val delta: Int inline get() = textLength - sourceLength

    override fun toString(): String = "ModificationEvent($start..<$end, '$text')"
}
