package dev.kikugie.antlr.tree

public interface CParseTreeVisitor<T, C> {
    public fun visit(tree: CParseTree, ctx: C): T
    public fun visitChildren(node: CRuleNode, ctx: C): T
    public fun visitTerminalNode(node: CTerminalNode, ctx: C): T
    public fun visitErrorNode(node: CErrorNode, ctx: C): T
}

public abstract class CAbstractParseTreeVisitor<T, C> : CParseTreeVisitor<T, C> {
    protected fun defaultResult(ctx: C): T = throw NotImplementedError()
    protected fun aggregateResult(prev: T, next: T, ctx: C): T = next
    protected fun shouldVisitNext(node: CRuleNode, result: T, ctx: C): Boolean = true

    override fun visit(tree: CParseTree, ctx: C): T = tree.accept(this, ctx)
    override fun visitErrorNode(node: CErrorNode, ctx: C): T = defaultResult(ctx)
    override fun visitTerminalNode(node: CTerminalNode, ctx: C): T = defaultResult(ctx)
    override fun visitChildren(node: CRuleNode, ctx: C): T {
        var result = defaultResult(ctx)
        for (i in 0 until node.childCount) {
            if (!shouldVisitNext(node, result, ctx)) break

            val next = node.getChild(i)
            if (next is CParseTree) result = aggregateResult(result, next.accept(this, ctx), ctx)
        }

        return result
    }
}
