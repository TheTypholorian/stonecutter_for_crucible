@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.util

import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.issue.ProblemLocation
import org.antlr.v4.runtime.ANTLRErrorListener
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.TokenFactory
import org.antlr.v4.runtime.TokenSource
import org.antlr.v4.runtime.misc.Pair

internal fun <T : AntlrToken> TokenFactory<T>.create(src: TokenSource, type: Int, start: Int, stop: Int, line: Int, offset: Int, input: CharStream? = null): T =
    create(Pair(src, input), type, null, Token.DEFAULT_CHANNEL, start, stop, line, offset)

internal fun <T : AntlrToken> TokenFactory<T>.create(src: Pair<TokenSource, CharStream>, type: Int, text: String?, range: IntRange, location: ProblemLocation): T =
    create(src, type, text, Token.DEFAULT_CHANNEL, range.first, range.last, location.line, location.column - 1)

internal fun <T : Recognizer<*, *>> T.errorListener(listener: ANTLRErrorListener) = apply {
    errorListeners.clear()
    (errorListeners as MutableList<ANTLRErrorListener>) += listener
}
