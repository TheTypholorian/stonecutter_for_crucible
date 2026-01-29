package dev.kikugie.stitcher.antlr

import dev.kikugie.stitcher.issue.ProblemLocation
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.misc.Pair

internal class InlineTokenStream(val source: Pair<TokenSource?, CharStream?>, val stream: TokenStream, val location: ProblemLocation) :
    TokenStream by stream {
    init {
        assert(location.fileIndex > -1) { "Absolute file location must be known" }
    }

    private val factory: TokenFactory<*> get() = stream.tokenSource.tokenFactory
    private var cache: Array<AntlrToken?> = arrayOfNulls(4)

    override fun LT(k: Int): Token = getCached(index().coerceAtLeast(0) + k - 1, stream.LT(k))
    override fun get(index: Int): Token = getCached(index, stream.get(index))
    override fun getText(interval: Interval): String = stream.getText(Interval.of(interval.a + location.fileIndex, interval.b + location.fileIndex))

    private fun getCached(index: Int, token: AntlrToken): AntlrToken {
        if (index >= cache.size) cache = cache.copyOf(cache.size * 2)
        return cache[index] ?: shiftToken(token).also { cache[index] = it }
    }

    private fun shiftToken(token: AntlrToken): AntlrToken {
        val location = location.resolve(token.lineIndex, token.charPositionInLine, token.startIndex)
        return factory.create(
            source,
            token.type,
            token.text,
            token.channel,
            token.startIndex + location.fileIndex,
            token.startIndex + location.fileIndex,
            location.line,
            location.charOffset
        )
    }
}