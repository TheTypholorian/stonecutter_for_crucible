package dev.kikugie.stitcher.document

import dev.kikugie.stitcher.document.issue.ProblemReporter
import java.nio.file.Path

/**
 * Represents contents of a file or its segment.
 */
interface Document : ProblemReporter, CharSequence {
    /** File on the disk associated with this document. */
    val file: Path

    /**
     * Matches a [charIndex] to [DocumentLocation] with line and column positions.
     * - The line break character (`\n`) counts to the line before it.
     * - If [charIndex] is out of bounds, [DocumentLocation.UNKNOWN] is returned.
     */
    fun locate(charIndex: Int): DocumentLocation

    /**
     * Matches [lineIndex] and [columnIndex] to a character index in the document.
     * - Both parameters are zero-indexed.
     * - Returns `-1` if the document is empty or either parameter is negative.
     * - Result is clamped to [length] if [lineIndex] is greater than the last line.
     * - Result is clamped to the line's `\n` character if [columnIndex] is past its boundaries.
     */
    fun indexOf(lineIndex: Int, columnIndex: Int): Int

    /**
     * Appends a part of the document in [startIndex]..<[endIndex] to the [builder].
     * - This method is more efficient than [subSequence], as it doesn't allocate new strings.
     */
    fun appendTo(builder: Appendable, startIndex: Int, endIndex: Int)
}