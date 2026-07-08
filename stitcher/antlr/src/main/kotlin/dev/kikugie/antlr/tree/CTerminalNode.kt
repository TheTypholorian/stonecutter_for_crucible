package dev.kikugie.antlr.tree

import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode
import org.antlr.v4.runtime.tree.TerminalNodeImpl

public interface CTerminalNode : CParseTree, TerminalNode

public class CTerminalNodeImpl(symbol: Token) : CTerminalNode, TerminalNode by TerminalNodeImpl(symbol) {
    override fun <T, C> accept(visitor: CParseTreeVisitor<out T, C>, ctx: C): T =
        visitor.visitTerminalNode(this, ctx)
}
