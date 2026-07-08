package dev.kikugie.antlr.tree

import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ErrorNodeImpl

public interface CErrorNode : CTerminalNode, ErrorNode

public class CErrorNodeImpl(symbol: Token) : CErrorNode, ErrorNode by ErrorNodeImpl(symbol) {
    override fun <T, C> accept(visitor: CParseTreeVisitor<out T, C>, ctx: C): T =
        visitor.visitErrorNode(this, ctx)
}
