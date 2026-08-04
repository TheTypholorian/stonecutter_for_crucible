package dev.kikugie.stitcher.document

import dev.kikugie.stitcher.document.issue.ProblemReport
import dev.kikugie.stitcher.document.issue.output.ProblemConsumer
import java.nio.file.Path

/**
 * Represents a document corresponding to the original [file] contents.
 */
@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
class SourceDocument(override val file: Path, val text: String, private val reporter: ProblemConsumer) : Document, CharSequence by text {
    override var errors: Long = 0L
        private set

    private val lines: DocumentLineIndex = DocumentLineIndex(text)

    override fun toString(): String = text

    override fun locate(charIndex: Int): DocumentLocation =
        lines.locate(charIndex)

    override fun indexOf(lineIndex: Int, columnIndex: Int): Int =
        lines.index(lineIndex, columnIndex)

    override fun appendTo(builder: Appendable, startIndex: Int, endIndex: Int) {
        builder.append(text, startIndex, endIndex)
    }

    override fun report(report: ProblemReport) {
        if (report.severity == ProblemReport.Severity.ERROR) errors++
        reporter.accept(report)
    }
}
