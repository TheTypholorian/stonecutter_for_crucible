@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.antlr

import dev.kikugie.antlr.tree.CParseTree
import dev.kikugie.antlr.tree.CParseTreeVisitor
import dev.kikugie.antlr.util.endIndex
import dev.kikugie.antlr.util.isEOF
import dev.kikugie.stitcher.token.StitcherType
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.ParseTreeVisitor

typealias AntlrToken = Token

val AntlrToken.lineIndex: Int
    inline get() = line - 1

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

inline fun <reified T> ParseTree.acceptAs(visitor: ParseTreeVisitor<in T>): T =
    accept(visitor) as T

inline fun <reified T, C> CParseTree.acceptAs(visitor: CParseTreeVisitor<in T, C>, ctx: C): T =
    accept(visitor, ctx) as T
