package dev.kikugie.stitcher.antlr

import dev.kikugie.antlr.util.endIndex
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.IntStream

class InlineCharStream(val stream: CharStream, val start: Int, val end: Int) : AutoCloseable, CharStream by stream {
    constructor(token: AntlrToken) : this(token.inputStream, token.startIndex, token.endIndex)

    val position: Int = stream.index()
    val marker: Int = stream.mark()

    init {
        stream.seek(start)
    }

    override fun seek(index: Int) {
        assert(index in start..end) { "Index $index is out of bounds $start..$end" }
        stream.seek(index)
    }

    override fun consume() {
        assert(index() < end) { "Cannot consume EOF" }
        stream.consume()
    }

    override fun close() {
        stream.seek(position)
        stream.release(marker)
    }

    override fun LA(i: Int): Int {
        val sh = if (i > 0) i - 1 else i
        return if (index() + sh in start..<end) stream.LA(i)
        else IntStream.EOF
    }
}
