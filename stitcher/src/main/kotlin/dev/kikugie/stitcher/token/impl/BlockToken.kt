package dev.kikugie.stitcher.token.impl

import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.util.firstNotNull
import dev.kikugie.stitcher.util.lastNotNull

sealed interface BlockToken : StitcherToken {
    fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T

    interface Visitor<T, C> {
        fun visitContent(content: Content, ctx: C): T
        fun visitComment(comment: Comment, ctx: C): T
        fun visitCode(code: Code, ctx: C): T
        fun visitRoot(root: Root, ctx: C): T
    }

    data class Content(val leaf: LeafToken) : BlockToken, StitcherToken by leaf {
        override val type: NativeStitcherType get() = NativeStitcherType.CONTENT

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitContent(this, ctx)
        fun isBlank(): Boolean = leaf.text.isBlank()
    }

    data class Comment(val opener: LeafToken?, val body: LeafToken, val closer: LeafToken?) : BlockToken {
        override val type: NativeStitcherType get() = NativeStitcherType.COMMENT
        override val start: Int = firstNotNull(opener, body).start
        override val end: Int = lastNotNull(body, closer).end

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitComment(this, ctx)
        fun isBlank(): Boolean = body.text.isBlank()
    }

    data class Code(val host: Comment, val code: CodeToken, val scope: List<BlockToken> = emptyList()) : BlockToken {
        override val type: NativeStitcherType get() = NativeStitcherType.CODE
        override val start: Int get() = host.start
        override val end: Int = lastNotNull(host, scope.lastOrNull()).end

        val marker: LeafToken inline get() = code.marker
        val definition: DefinitionToken inline get() = code.definition

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitCode(this, ctx)
    }

    data class Root(val scope: List<BlockToken>) : BlockToken {
        override val type: NativeStitcherType get() = NativeStitcherType.ROOT
        override val start: Int = scope.firstOrNull()?.start ?: 0
        override val end: Int = scope.lastOrNull()?.end ?: 0

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitRoot(this, ctx)
    }
}