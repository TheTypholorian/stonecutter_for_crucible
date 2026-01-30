package dev.kikugie.stitcher.token.impl

import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.util.firstNotNull
import dev.kikugie.stitcher.util.lastNotNull

sealed interface BlockToken : StitcherToken {
    fun <T> accept(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visitContent(content: Content): T
        fun visitComment(comment: Comment): T
        fun visitCode(code: Code): T
        fun visitRoot(root: Root): T
    }

    data class Content(val leaf: LeafToken) : BlockToken, StitcherToken by leaf {
        override val type: NativeStitcherType get() = NativeStitcherType.CONTENT

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitContent(this)
        fun isBlank(): Boolean = leaf.text.isBlank()
    }

    data class Comment(val opener: LeafToken?, val body: LeafToken, val closer: LeafToken?) : BlockToken {
        override val type: NativeStitcherType get() = NativeStitcherType.COMMENT
        override val start: Int = firstNotNull(opener, body).start
        override val end: Int = lastNotNull(body, closer).end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitComment(this)
        fun isBlank(): Boolean = body.text.isBlank()
    }

    data class Code(
        val host: Comment, val marker: LeafToken,
        val definition: DefinitionToken, val scope: List<BlockToken> = emptyList()
    ) : BlockToken {
        override val type: NativeStitcherType get() = NativeStitcherType.CODE
        override val start: Int get() = host.start
        override val end: Int = lastNotNull(host, scope.lastOrNull()).end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitCode(this)
    }

    data class Root(val scope: List<BlockToken>) : BlockToken {
        override val type: NativeStitcherType get() = NativeStitcherType.ROOT
        override val start: Int = scope.firstOrNull()?.start ?: 0
        override val end: Int = scope.lastOrNull()?.end ?: 0

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitRoot(this)
    }
}