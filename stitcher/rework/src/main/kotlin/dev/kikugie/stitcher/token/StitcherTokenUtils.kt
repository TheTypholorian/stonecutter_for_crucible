@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.token

import dev.kikugie.antlr.util.endIndex
import dev.kikugie.semver.data.Version
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.StitcherLexer.Tokens
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.DocumentReference
import dev.kikugie.stitcher.document.reference
import dev.kikugie.stitcher.token.type.StitcherTokenType
import org.antlr.v4.runtime.tree.TerminalNode

val StitcherToken?.typeValue: Int
    inline get() = this?.type?.value ?: AntlrToken.EOF

val BlockToken.Comment.openerStr: String
    inline get() = opener?.text.orEmpty()

val BlockToken.Comment.closerStr: String
    inline get() = closer?.text.orEmpty()

val DefinitionToken.SwapOpener.id: String
    inline get() = identifier.text

val DefinitionToken.SwapOpener.args: Sequence<String>
    inline get() = arguments.asSequence().map(LeafToken::unquote)

val DefinitionToken.ReplacementToggle.Entry.id: String
    inline get() = identifier.text

// Adding nodes to the trie is irreversible, so they need to be disabled later
val DefinitionToken.ReplacementLocal.Entry.id: String
    get() = "/synthetic/ ${source.text} -> ${target.text}"

val ExpressionToken.Constant.text: String
    inline get() = value.text

val ExpressionToken.Assignment.id: String
    inline get() = target?.text.orEmpty()

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

fun ExpressionToken.Assignment.eval(version: Version): Boolean =
    predicates.all { it.operator(version, it.version) }
