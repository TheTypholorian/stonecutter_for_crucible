@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.issue

import java.nio.file.Path

/**
 * Represents a location within a file where a problem was detected.
 *
 * Provides zero-indexed [lineIndex], [charOffset], and [fileIndex] properties,
 * along with one-indexed convenience properties [line] and [column].
 */
data class ProblemLocation(
    val file: Path,
    val lineIndex: Int = -1,
    val charOffset: Int = -1,
    val fileIndex: Int = -1,
) {
    val line: Int inline get() = lineIndex + 1
    val column: Int inline get() = charOffset + 1

    val isUndefined: Boolean
        inline get() = lineIndex < 0

    val isLineKnown: Boolean
        inline get() = lineIndex >= 0

    val isColumnKnown: Boolean
        inline get() = charOffset >= 0

    inline operator fun plus(other: ProblemLocation) = resolve(other)

    fun resolve(relative: ProblemLocation): ProblemLocation {
        assert(file == relative.file)
        val newLine = if (!isLineKnown || !relative.isLineKnown) -1 else lineIndex + relative.lineIndex
        val newOffset = when {
            !isColumnKnown || !relative.isColumnKnown -> -1
            relative.lineIndex > 0 -> relative.charOffset
            else -> charOffset + relative.charOffset
        }
        val newIndex = if (fileIndex < 0 || relative.fileIndex < 0) -1 else fileIndex + relative.fileIndex
        return ProblemLocation(file, newLine, newOffset, newIndex)
    }
}
