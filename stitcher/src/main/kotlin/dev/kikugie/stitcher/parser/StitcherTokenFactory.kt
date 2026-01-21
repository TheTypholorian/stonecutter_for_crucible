package dev.kikugie.stitcher.parser

import dev.kikugie.stitcher.token.StitcherType
import dev.kikugie.stitcher.token.impl.LeafToken
import dev.kikugie.stitcher.util.AntlrToken
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode

/**
 * Factory interface for creating [LeafToken] instances.
 *
 * The factory **must** be used in favour of direct instantiation,
 * as during the [BlockTransformer][dev.kikugie.stitcher.transform.BlockTransformer]
 * pass regions are evaluated as isolated files, with the factory carrying the
 * necessary token location modifiers.
 */
public interface StitcherTokenFactory {
    public fun create(type: StitcherType, start: Int, end: Int, text: String): LeafToken

    public class Inline(private val offset: Int) : StitcherTokenFactory {
        override fun create(type: StitcherType, start: Int, end: Int, text: String): LeafToken =
            LeafToken(type, start + offset, end + offset, text)
    }

    public companion object : StitcherTokenFactory {
        override fun create(type: StitcherType, start: Int, end: Int, text: String): LeafToken =
            LeafToken(type, start, end, text)
    }
}

public fun StitcherTokenFactory.fromAntlrToken(token: AntlrToken, type: StitcherType.TypeFlyweight): LeafToken =
    create(type[token.type], token.startIndex, token.stopIndex + 1, token.text)

public fun StitcherTokenFactory.fromAntlrNode(node: TerminalNode, type: StitcherType.TypeFlyweight): LeafToken =
    fromAntlrToken(node.symbol, type)

public fun StitcherTokenFactory.fromAntlrRule(rule: ParserRuleContext, type: StitcherType): LeafToken =
    create(type, rule.start.startIndex, rule.stop.stopIndex + 1, rule.text)
