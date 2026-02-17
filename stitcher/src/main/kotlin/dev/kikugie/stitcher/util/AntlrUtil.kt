@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.util

import dev.kikugie.commons.takeAs
import dev.kikugie.commons.takeAsOrNull
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.StitcherType
import dev.kikugie.stitcher.token.impl.LeafToken
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.Pair

internal fun <T : AntlrToken> TokenFactory<T>.create(
    src: TokenSource,
    input: CharStream?,
    type: StitcherType,
    start: Int,
    stop: Int,
    line: Int,
    offset: Int,
): T =
    create(Pair(src, input), type.value, null, AntlrToken.DEFAULT_CHANNEL, start, stop, line, offset)

internal fun <T : AntlrToken> TokenFactory<T>.create(
    src: Pair<TokenSource?, CharStream?>,
    token: StitcherToken,
    problems: FileProblemReporter,
    type: StitcherType = token.type
): T {
    val location = problems.index.locate(token.start)
    return create(
        src,
        type.value,
        token.takeAsOrNull<LeafToken>()?.text,
        AntlrToken.DEFAULT_CHANNEL,
        token.start,
        token.end - 1,
        location.line,
        location.charOffset
    )
}

private val Parser.previousToken: AntlrToken
    get() = tokenStream.LT(-1)

var Recognizer<*, *>.errorListener: ANTLRErrorListener
    get() = errorListeners.single()
    set(value) {
        errorListeners.clear()
        errorListeners.takeAs<MutableList<ANTLRErrorListener>>() += value
    }
