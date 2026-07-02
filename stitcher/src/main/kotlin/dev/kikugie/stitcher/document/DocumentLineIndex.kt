package dev.kikugie.stitcher.document

import dev.kikugie.stitcher.antlr.charStream
import org.antlr.v4.runtime.IntStream

@JvmInline
value class DocumentLineIndex private constructor(val positions: List<Int>) {
    constructor(text: String) : this(text.charStream().buildLineIndex())
    constructor(stream: IntStream) : this(stream.buildLineIndex())

    fun locate(index: Int): DocumentLocation {
        if (positions.isEmpty()) return DocumentLocation.UNKNOWN

        val lineIndex = searchIndex(index)
        val columnIndex = index - positions[lineIndex]
        return DocumentLocation(lineIndex, columnIndex)
    }

    fun reverse(lineIndex: Int, columnIndex: Int): Int {
        if (positions.isEmpty()) return 0
        return columnIndex + positions.getOrElse(lineIndex) { positions.last() }
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

private fun IntStream.advance(): Int =
    LA(1).also { if (it != IntStream.EOF) consume() }
