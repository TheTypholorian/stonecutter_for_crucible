package dev.kikugie.stitcher.document

import dev.kikugie.stitcher.document.edit.ModificationEvent
import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.commons.ranges.OpenIntRange

/**
 * Represents a modified portion of the [parent] document in range [start]..<[end].
 */
@Suppress("JavaDefaultMethodsNotOverriddenByDelegation", "UNCHECKED_CAST")
class InjectedDocument(val parent: Document, val start: Int, val end: Int, tracker: ModificationTracker) : Document by parent {
    constructor(tracker: ModificationTracker) : this(tracker.document, tracker.start, tracker.end, tracker)

    private val text: StringBuilder = StringBuilder()
    private val segments: Array<Segment> = arrayOfNulls<Segment>(tracker.toList().size) as Array<Segment>

    override val length: Int get() = text.length

    init {
        initImpl(tracker.toList())
    }

    override fun toString(): String = text.toString()

    /**
     * Gets a character at the given [index].
     * - The [index] may be in [minIndex]..<[maxIndex] (i.e. less than zero or more than [length]).
     *   In case it's not in 0..<[length], the [parent] document is queried.
     */
    override fun get(index: Int): Char = when (index) {
        in indices -> text[index]
        in 0..Int.MAX_VALUE -> parent[end + (index - length)]
        else -> parent[start + index]
    }

    /**
     * Matches a [charIndex] to [DocumentLocation] with line and column positions.
     * - The [charIndex] may be in [minIndex]..<[maxIndex] (i.e. less than zero or more than [length]).
     * - The line break character (`\n`) counts to the line before it.
     * - If [charIndex] is out of bounds, [DocumentLocation.UNKNOWN] is returned.
     */
    override fun locate(charIndex: Int): DocumentLocation = parent.locate(
        when (charIndex) {
            in indices -> toParentIndex(charIndex)
            in 0..Int.MAX_VALUE -> end + (charIndex - length)
            else -> start + charIndex
        }
    )

    /**
     * Matches [lineIndex] and [columnIndex] to a character index in the document.
     * - Both parameters correspond to positions in the [SourceDocument] ancestor,
     *   but the result is in the local (modified) space.
     * - Both parameters are zero-indexed.
     * - Returns `-1` if the document is empty or either parameter is negative.
     * - Result is clamped to [length] if [lineIndex] is greater than the last line.
     * - Result is clamped to the line's `\n` character if [columnIndex] is past its boundaries.
     */
    override fun indexOf(lineIndex: Int, columnIndex: Int): Int {
        val index = parent.indexOf(lineIndex, columnIndex)
        return if (index == -1) -1 else toLocalIndex(index - start)
    }

    /**
     * Appends a part of the document in [startIndex]..<[endIndex] to the [builder].
     * - The range may be in [minIndex]..<[maxIndex] (i.e. less than zero or more than [length]).
     *   In case it's not in 0..<[length], the [parent] document is queried as well.
     * - This method is more efficient than [subSequence], as it doesn't allocate new strings.
     */
    override fun appendTo(builder: Appendable, startIndex: Int, endIndex: Int) {
        if (startIndex < 0) parent.appendTo(builder, start + startIndex, start)
        builder.append(text, startIndex.coerceAtLeast(0), endIndex.coerceAtMost(length))
        if (endIndex > length) parent.appendTo(builder, end, end + (endIndex - length))
    }

    /**
     * Extracts a part of the document in [startIndex]..<[endIndex] as a new [StringBuilder].
     * - The range may be in [minIndex]..<[maxIndex] (i.e. less than zero or more than [length]).
     *   In case it's not in 0..<[length], the [parent] document is queried as well.
     */
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = StringBuilder().apply {
        appendTo(this, startIndex, endIndex)
    }

    /**
     * Maps a local [charIndex] (in the modified [text] space) back to a character index in the [parent].
     *
     * The segments are sorted by their [Segment.start], so the owning segment is the last one
     * beginning at or before [charIndex], found via binary search. If none does, [charIndex] lies in the
     * unmodified prefix and maps to the [parent] directly.
     */
    private fun toParentIndex(charIndex: Int): Int {
        val segment = findMatchingSegment { it.start <= charIndex }
        return segment?.parentIndexOf(charIndex) ?: (start + charIndex)
    }

    /**
     * Maps a window-relative source index (a [parent] index offset by [start]) into the modified [text] space.
     *
     * The segments are sorted by their source start, so the owning segment is the last one starting at or before
     * [sourceIndex], found via binary search. If none does, [sourceIndex] precedes every edit and is unchanged.
     */
    private fun toLocalIndex(sourceIndex: Int): Int {
        val segment = findMatchingSegment { it.sourceStart <= sourceIndex }
        return segment?.localIndexOf(sourceIndex) ?: sourceIndex
    }

    private inline fun findMatchingSegment(predicate: (Segment) -> Boolean): Segment? {
        var low = 0
        var high = segments.lastIndex
        var found: Segment? = null
        while (low <= high) {
            val mid = (high and low) + ((high xor low) ushr 1)
            val segment = segments[mid]
            if (!predicate(segment)) high = mid - 1 else {
                found = segment
                low = mid + 1
            }
        }
        return found
    }

    private fun initImpl(events: Collection<ModificationEvent>) {
        var runningDelta = 0
        var lastEventEnd = start

        for ((index, event) in events.withIndex()) {
            segments[index] = Segment(event, runningDelta, start)
            runningDelta += event.textLength - event.sourceLength

            if (lastEventEnd < event.start)
                parent.appendTo(text, lastEventEnd, event.start)
            text.append(event.text)
            lastEventEnd = event.end
        }

        if (lastEventEnd < end)
            parent.appendTo(text, lastEventEnd, end)
    }

    private class Segment(val event: ModificationEvent, val delta: Int, windowStart: Int) : OpenIntRange {
        // The event's [start]/[end] are parent indices; [sourceStart]/[sourceEnd] rebase them onto the window.
        override val start: Int = event.start - windowStart + delta
        override val end: Int = start + event.textLength

        val deltaAfter: Int = delta + event.delta
        val sourceStart: Int = event.start - windowStart
        private val sourceEnd: Int = event.end - windowStart

        /** Maps a modified-space [charIndex] owned by this segment to a [parent] index within [doc]. */
        context(doc: InjectedDocument)
        fun parentIndexOf(charIndex: Int): Int = when {
            // After this event, but before the next event
            charIndex >= end -> doc.start + charIndex - deltaAfter
            // Inside inserted or deleted text - can't be mapped to source
            event.textLength == 0 || event.sourceLength == 0 -> event.start
            // Inside a replacement - try to find the middle ground
            else -> doc.start + charIndex - delta
        }

        /** Maps a window-relative [sourceIndex] owned by this segment to a modified-space index. */
        fun localIndexOf(sourceIndex: Int): Int = when {
            // Within this event's source range, which collapses onto the start of its replacement text.
            sourceIndex < sourceEnd -> start
            // In the unmodified gap following this event.
            else -> sourceIndex + deltaAfter
        }
    }
}
