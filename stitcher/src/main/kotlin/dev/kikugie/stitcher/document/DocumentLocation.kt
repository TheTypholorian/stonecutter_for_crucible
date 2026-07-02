package dev.kikugie.stitcher.document

@JvmInline @Suppress("NOTHING_TO_INLINE")
value class DocumentLocation private constructor(val packed: Long) {
    constructor(lineIndex: Int, columnIndex: Int) : this(lineIndex.toLong() or (columnIndex.toLong() shl 32))

    val isKnown: Boolean inline get() = packed != -1L

    val lineIndex: Int inline get() = (packed and 0xFFFFFFFFL).toInt()
    val columnIndex: Int inline get() = (packed ushr 32).toInt()

    val line: Int inline get() = lineIndex + 1
    val column: Int inline get() = columnIndex + 1

    override fun toString(): String =
        "DocumentLocation(lineIndex=$lineIndex, columnIndex=$columnIndex)"

    inline operator fun component1(): Int = lineIndex
    inline operator fun component2(): Int = columnIndex

    inline fun resolve(relative: DocumentLocation): DocumentLocation =
        resolve(relative.lineIndex, relative.columnIndex)

    fun resolve(relativeLineIndex: Int, relativeColumnIndex: Int): DocumentLocation {
        if (packed in -1..0) return DocumentLocation(relativeLineIndex, relativeColumnIndex)

        val (myLineIndex, myColumnIndex) = this
        val newLineIndex = myLineIndex + relativeLineIndex
        val newColumnIndex = when (relativeLineIndex) {
            0 -> myColumnIndex + relativeColumnIndex
            else -> relativeColumnIndex
        }
        return DocumentLocation(newLineIndex, newColumnIndex)
    }

    companion object {
        val UNKNOWN: DocumentLocation = DocumentLocation(-1L)
    }
}