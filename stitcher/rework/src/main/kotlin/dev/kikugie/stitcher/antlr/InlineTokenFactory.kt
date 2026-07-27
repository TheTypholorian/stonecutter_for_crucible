package dev.kikugie.stitcher.antlr

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CommonToken
import org.antlr.v4.runtime.CommonTokenFactory
import org.antlr.v4.runtime.TokenSource
import org.antlr.v4.runtime.misc.Pair

class InlineTokenFactory(val lineIndex: Int, val charOffset: Int) : CommonTokenFactory() {
    override fun create(
        source: Pair<TokenSource?, CharStream?>?,
        type: Int,
        text: String?,
        channel: Int,
        start: Int,
        stop: Int,
        line: Int,
        charPositionInLine: Int
    ): CommonToken? {
        val offset = if (line == 1) charPositionInLine + charOffset else charPositionInLine
        val line = lineIndex + line
        return super.create(source, type, text, channel, start, stop, line, offset)
    }
}
