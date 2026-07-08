@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.document

import dev.kikugie.antlr.util.length
import dev.kikugie.stitcher.issue.*
import dev.kikugie.stitcher.util.NonOverlappingList
import dev.kikugie.stitcher.util.NonOverlappingList.Entry.Companion.length
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.misc.Interval
import java.nio.file.Path

@DslMarker
@Retention(AnnotationRetention.SOURCE)
private annotation class DocumentDsl

@DocumentDsl
interface Document : ProblemReporter {
    val path: Path
    val length: Int

    operator fun get(startIndex: Int, endIndex: Int): String
    fun locate(index: Int): DocumentLocation
}

class SourceDocument(override val path: Path, val source: CharStream, private val reporter: ProblemConsumer) : Document {
    private val lineIndex: DocumentLineIndex = DocumentLineIndex(source)
    private var problemCounter: UInt = 0u

    override val length: Int
        get() = source.length

    override fun get(startIndex: Int, endIndex: Int): String =
        source.getText(Interval.of(startIndex, endIndex - 1))

    override fun locate(index: Int): DocumentLocation =
        lineIndex.locate(index)

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
    private var textSegments: MutableList<Segment> = mutableListOf()
    private var lastModCount: UInt = 0u
    private var cachedLength: Int = end - start

    override val length: Int
        get() = updateState().let { cachedLength }

    override fun get(startIndex: Int, endIndex: Int): String {
        if (endIndex - startIndex <= 0) return ""
        updateState()

        val builder = StringBuilder()
        var startIndex = startIndex
        for (segment in textSegments.listIterator(findSegmentIndex(startIndex))) {
            startIndex = segment.appendTo(builder, startIndex, endIndex)
            if (startIndex >= endIndex) break
        }

        return builder.toString()
    }

    override fun locate(index: Int): DocumentLocation {
        if (index !in 0..<length) return DocumentLocation.UNKNOWN
        updateState()

        val segment = findSegmentIndex(index).let(textSegments::get)
        return parent.locate(start + segment.mapIndex(index))
    }

    private fun findSegmentIndex(index: Int): Int {
        val index = textSegments.binarySearch { it.start - index }
        return if (index < 0) (-index - 2).coerceAtLeast(0) else index
    }

    private fun updateState() {
        if (lastModCount == tracker.modified) return
        lastModCount = tracker.modified

        updateOffsetShifts()
    }

    private fun updateOffsetShifts() {
        textSegments.clear()
        val baseLength = end - start
        if (tracker.modifications.isEmpty()) {
            cachedLength = baseLength
            return
        }

        var cumulativeDelta = 0
        var lastEventEnd = 0

        for (event in tracker.modifications.iterator()) {
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
        fun mapIndex(index: Int): Int
        fun appendTo(builder: StringBuilder, startIndex: Int, endIndex: Int): Int
    }

    private class EventSegment(val event: ModificationTracker.Event, delta: Int) : Segment {
        override val start: Int = event.start + delta
        override val end: Int = event.start + event.textLength + delta

        override fun mapIndex(index: Int): Int = when {
            event.text.isEmpty() || event.length == 0 -> event.start
            else -> event.start + (index - start).coerceAtMost(event.textLength)
        }

        override fun appendTo(builder: StringBuilder, startIndex: Int, endIndex: Int): Int {
            if (startIndex >= end) return end
            if (start >= endIndex) return endIndex

            val textStart = startIndex - start
            val textEnd = (endIndex - startIndex).coerceAtMost(event.textLength)
            builder.append(event.text.substring(textStart, textEnd))

            return startIndex + textEnd
        }
    }

    private inner class ParentSegment(val textStart: Int, textEnd: Int, delta: Int) : Segment {
        override val start: Int = textStart + delta
        override val end: Int = textEnd + delta

        override fun mapIndex(index: Int): Int =
            textStart + (index - start)

        override fun appendTo(builder: StringBuilder, startIndex: Int, endIndex: Int): Int {
            if (startIndex >= end) return end
            if (start >= endIndex) return endIndex

            val startOffset = textStart + this@InjectedDocument.start + (startIndex - start)
            val textLength = endIndex.coerceAtMost(end) - startIndex
            builder.append(parent[startOffset, startOffset + textLength])

            return startIndex + textLength
        }
    }
}

class SyntheticDocument(val text: String) : Document {
    override val path: Nothing get() = throwUnsupported()
    override val length: Int get() = text.length

    override fun get(startIndex: Int, endIndex: Int): String =
        text.substring(startIndex, endIndex)

    override fun locate(index: Int): Nothing = throwUnsupported()
    override fun mark(): Nothing = throwUnsupported()
    override fun create(id: ProblemID): Nothing = throwUnsupported()
    override fun report(report: ProblemReport): Nothing = throwUnsupported()

    private inline fun throwUnsupported(message: String = "Synthetic documents don't correspond to a real file"): Nothing =
        throw UnsupportedOperationException(message)
}
