package dev.kikugie.stitcher.document

import dev.kikugie.stitcher.antlr.advance
import dev.kikugie.stitcher.antlr.charStream
import org.antlr.v4.runtime.IntStream

/**
 * A mapping of line positions within a document.
 *
 * @property positions Line positions; the first line is always at index 0.
 * @property length The number of characters in the document.
 */
class DocumentLineIndex private constructor(val positions: List<Int>, val length: Int) {
    constructor(text: String) : this(text.charStream().buildLineIndex(), text.length)
    constructor(stream: IntStream) : this(stream.buildLineIndex(), stream.size())

    /**
     * Returns the text range for the line at the [index], including the trailing `\n` character if present.
     *
     * If [index] is out of bounds, returns [IntRange.EMPTY].
     */
    fun line(index: Int): IntRange {
        if (length <= 0 || index !in 0 until length) return IntRange.EMPTY

        val lineIndex = searchIndex(index)
        val start = positions[lineIndex]
        val end = if (lineIndex == positions.lastIndex) length else positions[lineIndex + 1]
        return start..<end
    }

    /**
     * Returns the [DocumentLocation] representing the line and column at the [index].
     *
     * If [index] is exactly at the `\n` character, it counts towards the line before the break.
     * If [index] is out of bounds, returns [DocumentLocation.UNKNOWN].
     */
    fun locate(index: Int): DocumentLocation {
        if (length <= 0 || index !in 0 until length) return DocumentLocation.UNKNOWN

        val lineIndex = searchIndex(index)
        val columnIndex = index - positions[lineIndex]
        return DocumentLocation(lineIndex, columnIndex)
    }

    /**
     * Returns the character position for the [lineIndex] and [columnIndex].
     *
     * If [lineIndex] or [columnIndex] is out of bounds, returns -1.
     */
    fun reverse(lineIndex: Int, columnIndex: Int): Int {
        if (length <= 0) return -1

        val lineStart = positions.getOrElse(lineIndex) { return -1 }
        val lineEnd = if (lineIndex == positions.lastIndex) length else positions[lineIndex + 1]
        if (columnIndex !in 0 until (lineEnd - lineStart)) return -1

        return lineStart + columnIndex
    }

    private tailrec fun searchIndex(index: Int, min: Int = 0, max: Int = positions.lastIndex): Int {
        if (min == max) return min // Converged onto one position
        val middle = min + (max - min) / 2
        val low = positions[middle]
        val high = if (middle < positions.lastIndex) positions[middle + 1] else Int.MAX_VALUE

        return if (index in low..<high) middle
        else if (index < low) searchIndex(index, min, middle)
        else searchIndex(index, middle + 1, max)
    }
}

private fun IntStream.buildLineIndex(): List<Int> {
    if (LA(1) == IntStream.EOF) return emptyList()

    var index = -1
    return try {
        index = index()
        seek(0)
        collectLinePositions()
    } finally {
        if (index != -1) seek(index)
    }
}

private fun IntStream.collectLinePositions(): List<Int> = buildList(32) {
    this += 0
    while (true) when (advance()) {
        '\n'.code -> this += index()
        IntStream.EOF -> break
    }
}
