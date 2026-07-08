package dev.kikugie.antlr

import dev.kikugie.antlr.util.type
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

public interface ParseTreeAccessor<T : ParseTree> : Iterable<T> {
    public operator fun get(ordinal: Int): T
    public fun getOrNull(ordinal: Int): T?
    public fun toList(): List<T>
}

public class TerminalNodesAccessor(private val ctx: ParserRuleContext, public val type: Int) : ParseTreeAccessor<TerminalNode> {
    public override operator fun get(ordinal: Int): TerminalNode =
        getOrNull(ordinal) ?: throw MissingTerminalNodeException(ctx, type, ordinal)

    public override fun getOrNull(ordinal: Int): TerminalNode? = ctx.getToken(type, ordinal)
    public override fun toList(): List<TerminalNode> = ctx.getTokens(type)

    override fun iterator(): Iterator<TerminalNode> = if (ctx.children.isNullOrEmpty()) EmptyIterator else iterator {
        for (node in ctx.children) if (node is TerminalNode && node.type == type) yield(node)
    }
}

public class RuleContextsAccessor<T : ParserRuleContext>(private val ctx: ParserRuleContext, public val type: KClass<out T>) : ParseTreeAccessor<T> {
    override fun get(ordinal: Int): T =
        getOrNull(ordinal) ?: throw MissingRuleContextException(ctx, type, ordinal)

    override fun getOrNull(ordinal: Int): T? = ctx.getRuleContext(type.java, ordinal)
    override fun toList(): List<T> = ctx.getRuleContexts(type.java)

    override fun iterator(): Iterator<T> = if (ctx.children.isNullOrEmpty()) EmptyIterator else iterator {
        for (node in ctx.children) type.safeCast(node)?.let { yield(it) }
    }
}

private object EmptyIterator : Iterator<Nothing> {
    override fun hasNext(): Boolean = false
    override fun next(): Nothing = throw NoSuchElementException()
}
