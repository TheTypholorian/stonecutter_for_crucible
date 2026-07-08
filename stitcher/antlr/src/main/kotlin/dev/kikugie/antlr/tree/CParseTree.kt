package dev.kikugie.antlr.tree

import org.antlr.v4.runtime.tree.ParseTree

public interface CParseTree : ParseTree {
    public fun <T, C> accept(visitor: CParseTreeVisitor<out T, C>, ctx: C): T
}