@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.issue

import java.nio.file.Path

/**
 * Represents a location within a file where a problem was detected
 *
 * Provides zero-indexed [lineIndex], [charOffset], and [fileIndex] properties,
 * along with one-indexed convenience properties [line] and [column]
 *
 * @property file File that contains the problem
 * @property lineIndex Zero-indexed line number, or -1 when unknown
 * @property charOffset Zero-indexed column offset, or -1 when unknown
 * @property fileIndex Zero-indexed file offset, or -1 when unknown
 */
data class ProblemLocation(
    val file: Path,
    val lineIndex: Int = -1,
    val charOffset: Int = -1,
    val fileIndex: Int = -1,
) {
    /**
     * One-indexed line number, or 0 when unknown
     */
    val line: Int inline get() = lineIndex + 1

    /**
     * One-indexed column offset, or 0 when unknown
     */
    val column: Int inline get() = charOffset + 1

    /**
     * Returns true when line information is unavailable
     */
    val isUndefined: Boolean
        inline get() = lineIndex < 0

    /**
     * Returns true when a line index is available
     */
    val isLineKnown: Boolean
        inline get() = lineIndex >= 0

    /**
     * Returns true when a column offset is available
     */
    val isColumnKnown: Boolean
        inline get() = charOffset >= 0

    /**
     * Resolves this location against another location
     */
    inline operator fun plus(other: ProblemLocation) = resolve(other)

    /**
     * Resolves a relative location against this location
     */
    fun resolve(relative: ProblemLocation): ProblemLocation {
        assert(file == relative.file)
        return resolve(relative.lineIndex, relative.charOffset, relative.fileIndex)
    }

    fun resolve(relativeLineIndex: Int = -1, relativeCharOffset: Int = -1, relativeFileIndex: Int = -1): ProblemLocation {
        val newLine = if (!isLineKnown || relativeLineIndex == -1) -1 else lineIndex + relativeLineIndex
        val newOffset = when {
            !isColumnKnown || relativeCharOffset == -1 -> -1
            relativeLineIndex > 0 -> relativeCharOffset
            else -> charOffset + relativeCharOffset
        }
        val newIndex = if (fileIndex < 0 || relativeFileIndex < 0) -1 else fileIndex + relativeFileIndex
        return ProblemLocation(file, newLine, newOffset, newIndex)
    }
}
