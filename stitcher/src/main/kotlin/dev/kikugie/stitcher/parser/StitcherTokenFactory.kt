package dev.kikugie.stitcher.parser

import dev.kikugie.antlr.util.endIndex
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.issue.ProblemID
import dev.kikugie.stitcher.token.StitcherType
import dev.kikugie.stitcher.token.impl.LeafToken
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode

internal val LAYOUT_GROUP: ProblemID = ProblemID("file-layout")
internal val SYNTAX_GROUP: ProblemID = ProblemID("comment-syntax")

/**
 * Factory interface for creating [LeafToken] instances.
 *
 * The factory **must** be used in favour of direct instantiation,
 * as during the [BlockTransformer][dev.kikugie.stitcher.transformer.BlockTransformer]
 * pass regions are evaluated as isolated files, with the factory carrying the
 * necessary token location modifiers.
 */
interface StitcherTokenFactory {
    fun create(type: StitcherType, start: Int, end: Int, text: String): LeafToken

    class Inline(private val offset: Int) : StitcherTokenFactory {
        override fun create(type: StitcherType, start: Int, end: Int, text: String): LeafToken =
            LeafToken(type, start + offset, end + offset, text)
    }

    companion object : StitcherTokenFactory {
        override fun create(type: StitcherType, start: Int, end: Int, text: String): LeafToken =
            LeafToken(type, start, end, text)
    }
}

fun StitcherTokenFactory.fromAntlrToken(token: AntlrToken, type: StitcherType = StitcherType.of(token.type)): LeafToken =
    create(type, token.startIndex, token.endIndex, token.text)

fun StitcherTokenFactory.fromAntlrTokenOrNull(token: AntlrToken?, type: StitcherType = StitcherType.of(token?.type ?: -1)): LeafToken? =
    token?.run { create(type, startIndex, endIndex, text) }

fun StitcherTokenFactory.fromAntlrNode(node: TerminalNode, type: StitcherType = StitcherType.of(node.symbol.type)): LeafToken =
    fromAntlrToken(node.symbol, type)

fun StitcherTokenFactory.fromAntlrNodeOrNull(node: TerminalNode?, type: StitcherType = StitcherType.of(node?.symbol?.type ?: -1)): LeafToken? =
    node?.run { fromAntlrToken(symbol, type) }

fun StitcherTokenFactory.fromAntlrRule(rule: ParserRuleContext, type: StitcherType): LeafToken =
    create(type, rule.start.startIndex, rule.stop.endIndex, rule.text)

fun StitcherTokenFactory.fromAntlrRuleOrNull(rule: ParserRuleContext?, type: StitcherType): LeafToken? =
    rule?.run { create(type, start.startIndex, stop.endIndex, text) }
