@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.antlr

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.IntStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.TokenSource
import org.antlr.v4.runtime.TokenStream
import org.antlr.v4.runtime.Vocabulary
import org.antlr.v4.runtime.tree.TerminalNode
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path

typealias AntlrToken = org.antlr.v4.runtime.Token

val AntlrToken.isEOF: Boolean inline get() = type == AntlrToken.EOF
val AntlrToken.endIndex: Int inline get() = stopIndex + 1
val AntlrToken.lineIndex: Int inline get() = line - 1

val TerminalNode.type: Int inline get() = symbol.type
val TerminalNode.startIndex: Int inline get() = symbol.startIndex
val TerminalNode.endIndex: Int inline get() = symbol.endIndex
val TerminalNode.lineIndex: Int inline get() = symbol.lineIndex

val ParserRuleContext.isValid: Boolean inline get() = start != null && stop != null
val ParserRuleContext.startIndex: Int inline get() = start.startIndex
val ParserRuleContext.endIndex: Int inline get() = stop.endIndex

inline fun Vocabulary.displayName(token: AntlrToken): String = getDisplayName(token.type)
inline fun Vocabulary.indexedName(token: AntlrToken): String = indexedName(token.type)
fun Vocabulary.indexedName(type: Int): String = "$type ('${getDisplayName(type)}')"

fun String.charStream(source: String = CharStream.UNKNOWN_SOURCE_NAME): CharStream =
    CharStreams.fromString(this, source)

fun Path.charStream(charset: Charset = StandardCharsets.UTF_8): CharStream =
    CharStreams.fromPath(this, charset)

fun InputStream.charStream(charset: Charset = StandardCharsets.UTF_8, size: Long = -1L): CharStream =
    CharStreams.fromStream(this, charset, size)

fun TokenSource.toSequence(): Sequence<AntlrToken> =
    generateSequence { nextToken()?.takeUnless(AntlrToken::isEOF) }

fun TokenStream.toSequence(): Sequence<AntlrToken> =
    generateSequence { LT(1).takeUnless(AntlrToken::isEOF)?.also { consume() } }

fun CharStream.LC(offset: Int): Char = when(val it = LA(offset)) {
    IntStream.EOF -> '\u0000'
    else -> it.toChar()
}
