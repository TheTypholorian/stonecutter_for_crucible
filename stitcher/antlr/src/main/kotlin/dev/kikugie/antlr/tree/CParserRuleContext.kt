package dev.kikugie.antlr.tree

import org.antlr.v4.runtime.ParserRuleContext

public open class CParserRuleContext(parent: CParserRuleContext?, state: Int) : ParserRuleContext(parent, state), CRuleNode {
    public constructor() : this(null, -1)

    override fun <T, C> accept(visitor: CParseTreeVisitor<out T, C>, ctx: C): T =
        visitor.visitChildren(this, ctx)
}
