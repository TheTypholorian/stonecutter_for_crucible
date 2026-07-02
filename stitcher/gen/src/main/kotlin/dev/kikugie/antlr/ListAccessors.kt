package dev.kikugie.antlr

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import kotlin.reflect.KClass

public interface ParseTreeAccessor<T : ParseTree> {
    public operator fun get(ordinal: Int): T
    public fun getOrNull(ordinal: Int): T?
    public fun toList(): List<T>
}

public class TerminalNodesAccessor(private val ctx: ParserRuleContext, public val type: Int) : ParseTreeAccessor<TerminalNode> {
    public override operator fun get(ordinal: Int): TerminalNode =
        getOrNull(ordinal) ?: throw MissingTerminalNodeException(ctx, type, ordinal)

    public override fun getOrNull(ordinal: Int): TerminalNode? = ctx.getToken(type, ordinal)
    public override fun toList(): List<TerminalNode> = ctx.getTokens(type)
}

public class RuleContextsAccessor<T : ParserRuleContext>(private val ctx: ParserRuleContext, public val type: KClass<out T>) : ParseTreeAccessor<T> {
    override fun get(ordinal: Int): T =
        getOrNull(ordinal) ?: throw MissingRuleContextException(ctx, type, ordinal)

    override fun getOrNull(ordinal: Int): T? = ctx.getRuleContext(type.java, ordinal)
    override fun toList(): List<T> = ctx.getRuleContexts(type.java)

}
