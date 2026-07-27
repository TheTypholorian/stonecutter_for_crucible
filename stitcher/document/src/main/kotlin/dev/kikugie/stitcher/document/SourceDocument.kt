package dev.kikugie.stitcher.document

import dev.kikugie.stitcher.document.issue.ProblemID
import dev.kikugie.stitcher.document.issue.ProblemReport
import dev.kikugie.stitcher.document.issue.ProblemReporter
import dev.kikugie.stitcher.document.issue.output.ProblemConsumer
import java.nio.file.Path

/**
 * Represents a document corresponding to the original [file] contents.
 */
@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
class SourceDocument(override val file: Path, val text: CharSequence, private val reporter: ProblemConsumer) : Document, CharSequence by text {
    private val lines: DocumentLineIndex = DocumentLineIndex(text)
    private var problemCounter: Long = 0L

    override fun locate(charIndex: Int): DocumentLocation =
        lines.locate(charIndex)

    override fun indexOf(lineIndex: Int, columnIndex: Int): Int =
        lines.index(lineIndex, columnIndex)

    override fun appendTo(builder: Appendable, startIndex: Int, endIndex: Int) {
        builder.append(text, startIndex, endIndex)
    }

    override fun mark(): ProblemReporter.Marker =
        ProblemMarker(problemCounter)

    override fun create(id: ProblemID): ProblemReport =
        ProblemReport(id, this)

    override fun report(report: ProblemReport) {
        if (report.severity == ProblemReport.Severity.ERROR) problemCounter++
        reporter.accept(report)
    }

    private inner class ProblemMarker(private val countAtMarker: Long) : ProblemReporter.Marker {
        override fun isDirty(): Boolean = problemCounter != countAtMarker
    }
}
