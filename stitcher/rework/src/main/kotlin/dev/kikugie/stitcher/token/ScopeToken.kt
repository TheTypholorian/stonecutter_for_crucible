package dev.kikugie.stitcher.token

import dev.kikugie.stitcher.token.type.BuiltinTokenType

sealed interface ScopeToken : StitcherToken {
    fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T

    interface Visitor<T, C> {
        fun visitClosed(scope: Closed, ctx: C): T
        fun visitLookup(scope: Lookup, ctx: C): T
        fun visitNamed(scope: Named, ctx: C): T
    }

    data class Closed(val token: LeafToken) : ScopeToken {
        override val type: BuiltinTokenType get() = BuiltinTokenType.CLOSED_SCOPE

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitClosed(this, ctx)
    }

    data class Lookup(val marker: LeafToken, val plus: LeafToken? = null, val lookup: LeafToken? = null) : ScopeToken {
        override val type: BuiltinTokenType get() = BuiltinTokenType.LOOKUP_SCOPE

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitLookup(this, ctx)
    }

    data class Named(val marker: LeafToken, val identifier: LeafToken) : ScopeToken {
        override val type: BuiltinTokenType get() = BuiltinTokenType.NAMED_SCOPE

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitNamed(this, ctx)
    }
}
