@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.token

import dev.kikugie.antlr.util.endIndex
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.StitcherLexer.Tokens
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.reference
import dev.kikugie.stitcher.token.type.AntlrTokenType
import dev.kikugie.stitcher.token.type.StitcherTokenType
import org.antlr.v4.runtime.tree.TerminalNode

val StitcherToken?.typeValue: Int
    inline get() = this?.type?.value ?: AntlrToken.EOF

inline fun TerminalNode.toLeaf(doc: Document, type: StitcherTokenType = AntlrTokenType.of(symbol.type)): LeafToken =
    symbol.toLeaf(doc, type)

inline fun AntlrToken.toLeaf(doc: Document, type: StitcherTokenType = AntlrTokenType.of(this.type)): LeafToken =
    LeafToken(type, doc.reference(startIndex, endIndex))

fun LeafToken.unquote(): String = when(type.value) {
    Tokens.QUOTED -> text.removeSurrounding("'")
    else -> text
}
