package dev.kikugie.stitcher.util

import dev.kikugie.semver.data.Version
import dev.kikugie.stitcher.antlr.StitcherLexer
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.ExpressionToken
import dev.kikugie.stitcher.token.impl.LeafToken

fun LeafToken.unquote(): String = when(type.value) {
    StitcherLexer.QUOTED -> text.removeSurrounding("'")
    else -> text
}

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

val ExpressionToken.Constant.text: String
    inline get() = value.text

val ExpressionToken.Assignment.id: String
    inline get() = target?.text.orEmpty()

// Adding nodes to the trie is irreversible, so they need to be disabled later
val DefinitionToken.ReplacementLocal.Entry.identifier: String
    get() = "/synthetic/ ${source.text} -> ${target.text}"

fun ExpressionToken.Assignment.eval(version: Version): Boolean =
    predicates.all { it.parsed(version) }

