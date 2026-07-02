@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.antlr

import dev.kikugie.commons.takeAs
import dev.kikugie.stitcher.token.StitcherType
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.tree.ParseTreeVisitor
import org.antlr.v4.runtime.tree.TerminalNode

typealias AntlrToken = Token

val AntlrToken.isEOF: Boolean
    inline get() = type == AntlrToken.EOF
val AntlrToken.endIndex: Int
    inline get() = stopIndex + 1
val AntlrToken.lineIndex: Int
    inline get() = line - 1
var CommonToken.endIndex: Int
    inline get() = stopIndex + 1
    inline set(value) {
        stopIndex = value - 1
    }

val TerminalNode.type: Int
    inline get() = symbol.type
val TerminalNode.startIndex: Int
    inline get() = symbol.startIndex
val TerminalNode.endIndex: Int
    inline get() = symbol.endIndex
val TerminalNode.lineIndex: Int
    inline get() = symbol.lineIndex

val ParserRuleContext.isValid: Boolean
    inline get() = start != null && stop != null
val ParserRuleContext.startIndex: Int
    inline get() = start.startIndex
val ParserRuleContext.endIndex: Int
    inline get() = stop.endIndex

val CharStream.length: Int
    inline get() = size() - 1

inline fun Vocabulary.displayName(token: AntlrToken): String =
    getDisplayName(token.type)

inline fun Vocabulary.indexedName(token: AntlrToken): String =
    indexedName(token.type)

fun Vocabulary.indexedName(type: Int): String =
    "$type ('${getDisplayName(type)}')"

fun String.charStream(source: String = CharStream.UNKNOWN_SOURCE_NAME): CharStream =
    CharStreams.fromString(this, source)

fun AntlrToken.toFullString(): String =
    "$type ${StitcherType.of(type).name}($startIndex..<$endIndex:$lineIndex:$charPositionInLine): '${text.replace("\n", "\\n")}'"

fun TokenStream.advance(): AntlrToken =
    LT(1).also { if (!it.isEOF) consume() }

fun CharStream.advance(): Char =
    LC(1).also { if (it != '\u0000') consume() }

fun IntStream.advance(): Int =
    LA(1).also { if (it != IntStream.EOF) consume() }

fun TokenSource.toSequence(): Sequence<AntlrToken> =
    generateSequence { nextToken()?.takeUnless(AntlrToken::isEOF) }

fun TokenStream.toSequence(): Sequence<AntlrToken> =
    generateSequence { LT(1).takeUnless(AntlrToken::isEOF)?.also { consume() } }

fun CharStream.LC(offset: Int): Char = when (val it = LA(offset)) {
    IntStream.EOF -> '\u0000'
    else -> it.toChar()
}

inline fun TokenSource.forEach(action: (AntlrToken) -> Unit) {
    var next: AntlrToken
    while (!nextToken().also { next = it }.isEOF) action(next)
}

inline fun TokenStream.forEach(action: (AntlrToken) -> Unit) {
    var next: AntlrToken
    while (!LT(1).also { next = it }.isEOF) {
        action(next)
        consume()
    }
}

inline fun <reified T> RuleContext.acceptAs(visitor: ParseTreeVisitor<in T>): T =
    accept(visitor).takeAs()
