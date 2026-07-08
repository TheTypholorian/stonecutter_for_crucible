package dev.kikugie.antlr.parser

import dev.kikugie.antlr.tree.CParserRuleContext
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.TokenStream

public abstract class CParser(input: TokenStream) : Parser(input) {
    override fun getContext(): CParserRuleContext? =
        _ctx as? CParserRuleContext

    override fun setContext(ctx: ParserRuleContext?): Unit =
        super.setContext(ctx as? CParserRuleContext)
}