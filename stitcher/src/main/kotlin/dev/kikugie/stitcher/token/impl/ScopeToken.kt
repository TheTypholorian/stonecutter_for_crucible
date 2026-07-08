package dev.kikugie.stitcher.token.impl

import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.util.lastNotNull

sealed interface ScopeToken : StitcherToken {
    fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T

    interface Visitor<T, C> {
        fun visitClosed(scope: Closed, ctx: C): T
        fun visitLookup(scope: Lookup, ctx: C): T
        fun visitNamed(scope: Named, ctx: C): T
    }

    data class Closed(val marker: LeafToken) : ScopeToken, StitcherToken by marker {
        override val type: NativeStitcherType get() = NativeStitcherType.CLOSED_SCOPE
        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitClosed(this, ctx)
    }

    data class Lookup(val marker: LeafToken, val plus: LeafToken? = null, val lookup: LeafToken? = null) : ScopeToken {
        override val type: NativeStitcherType get() = NativeStitcherType.LOOKUP_SCOPE
        override val start: Int get() = marker.start
        override val end: Int = lastNotNull(marker, plus, lookup).end
        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitLookup(this, ctx)
    }

    data class Named(val marker: LeafToken, val identifier: LeafToken) : ScopeToken {
        override val type: NativeStitcherType get() = NativeStitcherType.NAMED_SCOPE
        override val start: Int get() = marker.start
        override val end: Int get() = identifier.end
        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitNamed(this, ctx)
    }
}
