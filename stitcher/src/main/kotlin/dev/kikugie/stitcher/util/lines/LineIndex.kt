package dev.kikugie.stitcher.util.lines

import dev.kikugie.stitcher.antlr.flex.FlexLexerAdapter
import dev.kikugie.stitcher.antlr.flex.forEach
import dev.kikugie.stitcher.issue.ProblemLocation
import java.nio.file.Path

private val LINES_FACTORY: FlexLexerAdapter.Factory<LineToken> = FlexLexerAdapter.Factory { LineLexer(null) }

/**
 * Maps character indices in text to line and column positions using binary search.
 *
 * The [lines] list contains the starting character index of each line in the original text.
 * Provides [locate] methods to convert a character index to a [Location] or [ProblemLocation].
 */
@JvmInline @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
value class LineIndex(val lines: List<Int>) : List<Int> by lines {
    constructor(text: CharSequence) : this(buildLineIndex(text))

    /**Converts a character [index] to a [Location] with line and column information.*/
    fun locate(index: Int): Location {
        val lineIndex = findLineIndex(index)
        val charOffset = index - lines[lineIndex]
        return Location(lineIndex, charOffset)
    }

    /**Converts a character index to a [ProblemLocation] with file, line, and column information.*/
    fun locate(file: Path, index: Int): ProblemLocation {
        val lineIndex = findLineIndex(index)
        val charOffset = index - lines[lineIndex]
        return ProblemLocation(file, lineIndex, charOffset, index)
    }

    private tailrec fun findLineIndex(index: Int, min: Int = 0, max: Int = lines.lastIndex): Int {
        if (min == max) return min // Converged onto one position
        val middle = min + (max - min) / 2
        val low = lines[middle]
        val high = if (middle < lines.lastIndex) lines[middle + 1] else Int.MAX_VALUE

        return if (index in low..<high) middle
        else if (index < low) findLineIndex(index, min, middle)
        else findLineIndex(index, middle + 1, max)
    }

    /**Represents a source location with line and column information, stored compactly in a single Long value.*/
    @JvmInline
    value class Location private constructor(@PublishedApi internal val packed: Long) {
        constructor(line: Int, column: Int) : this((line.toLong() and 0xFFFFFFFFL) or (column.toLong() shl 32))
        val lineIndex: Int inline get() = (packed and 0xFFFFFFFFL).toInt()
        val charOffset: Int inline get() = (packed ushr 32 and 0xFFFFFFFFL).toInt()

        val line: Int inline get() = lineIndex + 1
        val column: Int inline get() = charOffset + 1

        operator fun component0(): Int = lineIndex
        operator fun component1(): Int = charOffset

        /**Converts this location to a [ProblemLocation] for the specified [file].*/
        fun toProblemLocation(file: Path): ProblemLocation = ProblemLocation(file, lineIndex, charOffset)
    }
}

internal data object LineToken

private fun buildLineIndex(text: CharSequence): List<Int> {
    val builder = mutableListOf(0)
    LINES_FACTORY.create(text).forEach {
        builder += it.tokenEnd
    }
    return builder.toList()
}