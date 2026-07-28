@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.token

import dev.kikugie.antlr.util.endIndex
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.StitcherLexer.Tokens
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.DocumentReference
import dev.kikugie.stitcher.document.reference
import dev.kikugie.stitcher.token.type.StitcherTokenType
import org.antlr.v4.runtime.tree.TerminalNode

val StitcherToken?.typeValue: Int
    inline get() = this?.type?.value ?: AntlrToken.EOF

fun TerminalNode.toLeaf(doc: Document, type: StitcherTokenType = StitcherTokenType.of(symbol.type)): LeafToken =
    symbol.toLeaf(doc, type)

fun AntlrToken.toLeaf(doc: Document, type: StitcherTokenType = StitcherTokenType.of(this.type)): LeafToken =
    LeafToken(type, doc.reference(startIndex, endIndex))

fun LeafToken.unquote(): String = when(type.value) {
    Tokens.QUOTED -> text.removeSurrounding("'")
    else -> text
}

inline fun LeafToken.withRef(builder: DocumentReference.() -> DocumentReference): LeafToken =
    copy(ref = ref.builder())
