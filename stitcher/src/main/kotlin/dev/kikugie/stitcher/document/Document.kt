@file:Suppress("NOTHING_TO_INLINE", "JavaDefaultMethodsNotOverriddenByDelegation")

package dev.kikugie.stitcher.document

import dev.kikugie.stitcher.issue.*
import dev.kikugie.stitcher.util.NonOverlappingList
import dev.kikugie.stitcher.util.NonOverlappingList.Entry.Companion.length
import java.nio.file.Path

@DslMarker
@Retention(AnnotationRetention.SOURCE)
private annotation class DocumentDsl

@DocumentDsl
interface Document : CharSequence, ProblemReporter {
    val path: Path

    fun locate(index: Int): DocumentLocation
    fun index(lineIndex: Int, columnIndex: Int): Int
    fun join(builder: Appendable, startIndex: Int, endIndex: Int)
}

class SourceDocument(override val path: Path, val source: String, private val reporter: ProblemConsumer) : Document, CharSequence by source {
    private val lines: DocumentLineIndex = DocumentLineIndex(source)
    private var problemCounter: UInt = 0u

    override fun locate(index: Int): DocumentLocation =
        lines.locate(index)

    override fun index(lineIndex: Int, columnIndex: Int): Int =
        lines.index(lineIndex, columnIndex)

    override fun join(builder: Appendable, startIndex: Int, endIndex: Int) {
        builder.append(source, startIndex, endIndex)
    }

    override fun mark(): ProblemReporter.Marker =
        ProblemMarker(problemCounter)

    override fun create(id: ProblemID): ProblemReport =
        ProblemReport(id, this)

    override fun report(report: ProblemReport) {
        if (report.sev == ProblemSeverity.ERROR) problemCounter++
        reporter.accept(report)
    }

    private inner class ProblemMarker(private val countAtMarker: UInt) : ProblemReporter.Marker {
        override fun isDirty(): Boolean = problemCounter > countAtMarker
    }
}

class InjectedDocument(val parent: Document, val start: Int, val end: Int, val tracker: ModificationTracker) : Document by parent {
    constructor(parent: Document, start: Int, end: Int) : this(parent, start, end, ModificationTracker(parent, end - start))
    private var textSegments: MutableList<Segment> = mutableListOf()
    private var lastModCount: UInt = 0u
    private var cachedLength: Int = end - start

    override val length: Int
        get() = updateState().let { cachedLength }

    override fun get(index: Int): Char = when {
        index < 0 -> parent[start + index]
        index > length -> parent[end + (index - length)]
        else -> textSegments[findSegmentIndex(index)].get(index)
    }

    override fun locate(index: Int): DocumentLocation = when {
        index < 0 -> parent.locate(start + index)
        index > length -> parent.locate(end + (index - length))
        else -> parent.locate(start + textSegments[findSegmentIndex(index)].map(index))
    }

    override fun index(lineIndex: Int, columnIndex: Int): Int {
        val index = parent.index(lineIndex, columnIndex)
        return if (index == -1) -1 else index - start
    }

    override fun join(builder: Appendable, startIndex: Int, endIndex: Int) {
        if (endIndex <= startIndex) return

        // Handle joining text before this injected fragment
        if (startIndex < 0) parent.join(builder, start + startIndex, start)

        var localStartIndex = startIndex.coerceAtLeast(0)
        val localEndIndex = endIndex.coerceAtMost(length)
        for (index in findSegmentIndex(startIndex) until textSegments.size) {
            localStartIndex = textSegments[index].join(builder, localStartIndex, localEndIndex)
            if (localStartIndex >= localEndIndex) break
        }

        // Handle joining text after this injected fragment
        if (endIndex > length) parent.join(builder, end, end + (endIndex - length))
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = StringBuilder().apply {
        join(this, startIndex, endIndex)
    }

    private fun findSegmentIndex(index: Int): Int {
        updateState()
        val index = textSegments.binarySearch { it.start - index }
        return if (index < 0) (-index - 2).coerceAtLeast(0) else index
    }

    private fun updateState() {
        if (lastModCount == tracker.timesModified) return
        lastModCount = tracker.timesModified

        updateOffsetShifts()
    }

    private fun updateOffsetShifts() {
        textSegments.clear()
        val baseLength = end - start
        if (tracker.changeList.isEmpty()) {
            cachedLength = baseLength
            return
        }

        var cumulativeDelta = 0
        var lastEventEnd = 0

        for (event in tracker.changeList.iterator()) {
            if (event.start > lastEventEnd)
                textSegments += ParentSegment(lastEventEnd, event.start, cumulativeDelta)
            textSegments += EventSegment(event, cumulativeDelta)
            cumulativeDelta += (event.textLength - event.length)
            lastEventEnd = event.end
        }
        if (lastEventEnd < baseLength)
            textSegments += ParentSegment(lastEventEnd, baseLength, cumulativeDelta)

        cachedLength = baseLength + cumulativeDelta
    }

    private sealed interface Segment : NonOverlappingList.Entry {
        fun get(index: Int): Char
        fun map(index: Int): Int
        fun join(builder: Appendable, startIndex: Int, endIndex: Int): Int
    }

    private class EventSegment(val event: ModificationTracker.Event, delta: Int) : Segment {
        override val start: Int = event.start + delta
        override val end: Int = event.start + event.textLength + delta

        override fun get(index: Int): Char =
            event.text[index - start]

        override fun map(index: Int): Int = when {
            event.text.isEmpty() || event.length == 0 -> event.start
            else -> event.start + (index - start).coerceAtMost(event.textLength)
        }

        override fun join(builder: Appendable, startIndex: Int, endIndex: Int): Int {
            // The requested range is after this segment
            if (startIndex >= end) return end

            // The requested range is before this segment
            if (start >= endIndex) return endIndex

            val textStart = startIndex - start
            val textEnd = (endIndex - startIndex).coerceAtMost(event.textLength)
            builder.append(event.text, textStart, textEnd)

            return startIndex + textEnd
        }
    }

    private inner class ParentSegment(val textStart: Int, textEnd: Int, delta: Int) : Segment {
        override val start: Int = textStart + delta
        override val end: Int = textEnd + delta

        private val baseStartOffset: Int = this@InjectedDocument.start - delta

        override fun get(index: Int): Char =
            parent[baseStartOffset + index]

        override fun map(index: Int): Int =
            textStart + (index - start)

        override fun join(builder: Appendable, startIndex: Int, endIndex: Int): Int {
            // The requested range is after this segment
            if (startIndex >= end) return end

            // The requested range is before this segment
            if (start >= endIndex) return endIndex

            val textStart = baseStartOffset + startIndex
            val textLength = endIndex.coerceAtMost(end) - startIndex
            parent.join(builder, textStart, textStart + textLength)

            return startIndex + textLength
        }
    }
}

class SyntheticDocument(val text: String) : Document, CharSequence by text {
    override val path: Nothing get() = throwUnsupported()

    override fun join(builder: Appendable, startIndex: Int, endIndex: Int) {
        builder.append(text, startIndex, endIndex)
    }

    override fun index(lineIndex: Int, columnIndex: Int): Int = -1

    override fun locate(index: Int): Nothing = throwUnsupported()
    override fun mark(): Nothing = throwUnsupported()
    override fun create(id: ProblemID): Nothing = throwUnsupported()
    override fun report(report: ProblemReport): Nothing = throwUnsupported()

    private inline fun throwUnsupported(message: String = "Synthetic documents don't correspond to a real file"): Nothing =
        throw UnsupportedOperationException(message)
}
