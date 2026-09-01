package dev.kikugie.antlr

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.IntStream
import org.antlr.v4.runtime.misc.Interval
import kotlin.math.min

public class CharSequenceCharStream(
    private val text: CharSequence,
    private val name: String = IntStream.UNKNOWN_SOURCE_NAME,
) : CharStream {
    private var position: Int = 0

    override fun index(): Int = position
    override fun size(): Int = text.length
    override fun getSourceName(): String = name
    override fun toString(): String = text.toString()

    override fun mark(): Int = -1
    override fun release(marker: Int): Unit = Unit

    override fun seek(index: Int) {
        position = min(index, text.length)
    }

    override fun consume() {
        check(position < text.length) { "Cannot consume EOF" }
        position++
    }

    override fun LA(i: Int): Int {
        if (i == 0) return 0
        val offset = if (i < 0) i else i - 1
        val index = position + offset
        return if (index !in text.indices) IntStream.EOF else text[index].code
    }

    override fun getText(interval: Interval): String {
        if (interval.a >= text.length) return ""
        return text.substring(interval.a, min(interval.b + 1, text.length))
    }
}
