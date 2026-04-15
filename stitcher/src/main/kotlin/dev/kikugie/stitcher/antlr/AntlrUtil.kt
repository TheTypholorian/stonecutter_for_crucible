@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.antlr

import dev.kikugie.commons.takeAs
import dev.kikugie.stitcher.token.StitcherType
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.tree.ParseTreeVisitor
import org.antlr.v4.runtime.tree.TerminalNode
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path

typealias AntlrToken = Token

/** True when the token type is EOF. */
val AntlrToken.isEOF: Boolean inline get() = type == AntlrToken.EOF
/** Exclusive end index for the token range. */
val AntlrToken.endIndex: Int inline get() = stopIndex + 1
/** Zero-based line number for the token. */
val AntlrToken.lineIndex: Int inline get() = line - 1

var CommonToken.endIndex: Int
    inline get() = stopIndex + 1
    inline set(value) {
        stopIndex = value - 1
    }

/** Token type for this terminal node. */
val TerminalNode.type: Int inline get() = symbol.type
/** Inclusive start index for the terminal node. */
val TerminalNode.startIndex: Int inline get() = symbol.startIndex
/** Exclusive end index for the terminal node. */
val TerminalNode.endIndex: Int inline get() = symbol.endIndex
/** Zero-based line number for the terminal node. */
val TerminalNode.lineIndex: Int inline get() = symbol.lineIndex

/** True when the context has both start and stop tokens. */
val ParserRuleContext.isValid: Boolean inline get() = start != null && stop != null
/** Inclusive start index for the rule context. */
val ParserRuleContext.startIndex: Int inline get() = start.startIndex
/** Exclusive end index for the rule context. */
val ParserRuleContext.endIndex: Int inline get() = stop.endIndex

/** Returns the display name for a token via its vocabulary. */
inline fun Vocabulary.displayName(token: AntlrToken): String = getDisplayName(token.type)
/** Returns a display name prefixed with the token type number. */
inline fun Vocabulary.indexedName(token: AntlrToken): String = indexedName(token.type)
/** Returns a display name prefixed with the token type number. */
fun Vocabulary.indexedName(type: Int): String = "$type ('${getDisplayName(type)}')"

/** Builds a CharStream from a string with an optional source name. */
fun String.charStream(source: String = CharStream.UNKNOWN_SOURCE_NAME): CharStream =
    CharStreams.fromString(this, source)

fun AntlrToken.repr(): String =
    "$type ${StitcherType.of(type).name}($startIndex..<$endIndex:$lineIndex:$charPositionInLine): '${text.replace("\n", "\\n")}'"

/** Builds a CharStream from a Path using the specified charset. */
fun Path.charStream(charset: Charset = StandardCharsets.UTF_8): CharStream =
    CharStreams.fromPath(this, charset)

/** Builds a CharStream from an InputStream with the given charset and size hint. */
fun InputStream.charStream(charset: Charset = StandardCharsets.UTF_8, size: Long = -1L): CharStream =
    CharStreams.fromStream(this, charset, size)

/** Streams tokens from a TokenSource until EOF. */
fun TokenSource.toSequence(): Sequence<AntlrToken> =
    generateSequence { nextToken()?.takeUnless(AntlrToken::isEOF) }

/** Streams tokens from a TokenStream, consuming as it goes. */
fun TokenStream.toSequence(): Sequence<AntlrToken> =
    generateSequence { LT(1).takeUnless(AntlrToken::isEOF)?.also { consume() } }

/** Returns the character at lookahead offset or NUL on EOF. */
fun CharStream.LC(offset: Int): Char = when(val it = LA(offset)) {
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

@JvmInline
@Deprecated("For now it's badly inefficient: https://youtrack.jetbrains.com/issue/KT-84300/Iterator-value-classes-are-boxed-on-each-iteration")
value class TokenStreamIterator(val stream: TokenStream) : Iterator<AntlrToken> {
    override fun hasNext(): Boolean = !stream.LT(1).isEOF
    override fun next(): AntlrToken = stream.LT(1).also {
        if (it.isEOF) throw NoSuchElementException("Reached EOF token")
        else stream.consume()
    }
}
