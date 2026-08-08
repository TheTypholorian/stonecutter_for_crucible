package dev.kikugie.stitcher.document

import dev.kikugie.stitcher.document.issue.ProblemReporter
import java.nio.file.Path

/**
 * Represents a document corresponding to the original [file] contents.
 */
@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
class SourceDocument(override val file: Path, val text: String, reporter: ProblemReporter) :
    Document, ProblemReporter by reporter, CharSequence by text {
    private val lines: DocumentLineIndex = DocumentLineIndex(text)

    override fun toString(): String = text

    override fun locate(charIndex: Int): DocumentLocation =
        lines.locate(charIndex)

    override fun indexOf(lineIndex: Int, columnIndex: Int): Int =
        lines.index(lineIndex, columnIndex)

    override fun appendTo(builder: Appendable, startIndex: Int, endIndex: Int) {
        builder.append(text, startIndex, endIndex)
    }
}
