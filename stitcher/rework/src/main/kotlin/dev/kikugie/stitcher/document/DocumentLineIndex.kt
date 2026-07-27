package dev.kikugie.stitcher.document

/**
 * A mapping of line positions within a document.
 *
 * @property positions Line positions; the first line is always at index 0.
 * @property length The number of characters in the document.
 */
class DocumentLineIndex private constructor(val positions: List<Int>, val length: Int) {
    constructor(text: CharSequence) : this(buildLineIndex(text), text.length)

    /**
     * Returns the [DocumentLocation] representing the line and column at the [index].
     * - If [index] is exactly at the `\n` character, it counts towards the line before the break.
     * - If [index] is out of bounds, returns [DocumentLocation.UNKNOWN].
     */
    fun locate(index: Int): DocumentLocation {
        if (length <= 0 || index !in 0 until length) return DocumentLocation.UNKNOWN

        val lineIndex = searchIndex(index)
        val columnIndex = index - positions[lineIndex]
        return DocumentLocation(lineIndex, columnIndex)
    }

    /**
     * Returns the character position for the [lineIndex] and [columnIndex].
     * - If the text is empty, [lineIndex] is negative, or [lineIndex] is negative, returns `-1`.
     * - If [lineIndex] is beyond the last line, returns [length].
     * - If [columnIndex] is beyond the line boundary, returns the index of the line's '\n' character.
     */
    fun index(lineIndex: Int, columnIndex: Int): Int {
        if (length <= 0 || lineIndex < 0 || columnIndex < 0) return -1

        val lineStart = positions.getOrElse(lineIndex) { return length }
        val lineEnd = if (lineIndex == positions.lastIndex) length else positions[lineIndex + 1]
        val columnIndex = columnIndex.coerceAtMost(lineEnd - lineStart - 1).coerceAtLeast(0)
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

private fun buildLineIndex(text: CharSequence): List<Int> =
    if (text.isEmpty()) emptyList()
    else mutableListOf(0).apply { collectLineIndexes(this, text, 0) }

private tailrec fun collectLineIndexes(list: MutableList<Int>, text: CharSequence, offset: Int): Unit = when (val it = text.indexOf('\n', offset)) {
    -1 -> {}
    else -> collectLineIndexes(list, text, (it + 1).also(list::add))
}
