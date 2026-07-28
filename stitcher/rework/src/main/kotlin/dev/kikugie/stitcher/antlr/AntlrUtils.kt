package dev.kikugie.stitcher.antlr

import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.token.type.StitcherTokenType
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.Pair

typealias AntlrToken = Token

var Recognizer<*, *>.errorListener: ANTLRErrorListener
    get() = errorListeners.single()
    set(value) {
        errorListeners.clear()
        errorListeners as MutableList<ANTLRErrorListener> += value
    }

internal fun <T : AntlrToken> TokenFactory<T>.create(
    src: TokenSource,
    input: CharStream?,
    type: StitcherTokenType,
    start: Int,
    stop: Int,
    line: Int,
    offset: Int,
): T =
    create(Pair(src, input), type.value, null, AntlrToken.DEFAULT_CHANNEL, start, stop, line, offset)

internal fun <T : AntlrToken> TokenFactory<T>.create(
    src: Pair<TokenSource?, CharStream?>,
    token: LeafToken,
    document: Document,
    type: StitcherTokenType = token.type
): T {
    val location = document.locate(token.start)
    return create(
        src,
        type.value,
        token.text,
        AntlrToken.DEFAULT_CHANNEL,
        token.start,
        token.end - 1,
        location.line,
        location.columnIndex
    )
}
