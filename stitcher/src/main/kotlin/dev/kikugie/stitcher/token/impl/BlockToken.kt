package dev.kikugie.stitcher.token.impl

import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.StitcherType
import dev.kikugie.stitcher.util.firstNotNull
import dev.kikugie.stitcher.util.lastNotNull

public sealed interface BlockToken : StitcherToken {
    public fun <T> accept(visitor: Visitor<T>): T

    public interface Visitor<T> {
        public fun visitContent(content: Content): T
        public fun visitComment(comment: Comment): T
        public fun visitCode(code: Code): T
        public fun visitRoot(root: Root): T
    }

    public data class Content(public val leaf: LeafToken) : BlockToken, StitcherToken by leaf {
        override val type: StitcherType get() = StitcherType.Composite.CONTENT

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitContent(this)
    }

    public data class Comment(val opener: LeafToken?, val body: LeafToken, val closer: LeafToken?) : BlockToken {
        override val type: StitcherType get() = StitcherType.Composite.COMMENT
        override val start: Int = firstNotNull(opener, body).start
        override val end: Int = lastNotNull(body, closer).end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitComment(this)
    }

    public data class Code(
        val host: Comment, val marker: LeafToken,
        val definition: DefinitionToken, val scope: List<BlockToken> = emptyList()
    ) : BlockToken {
        override val type: StitcherType get() = StitcherType.Composite.CODE
        override val start: Int get() = host.start
        override val end: Int = lastNotNull(host, scope.lastOrNull()).end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitCode(this)
    }

    public data class Root(val scope: List<BlockToken>) : BlockToken {
        override val type: StitcherType get() = StitcherType.Composite.ROOT
        override val start: Int = scope.firstOrNull()?.start ?: 0
        override val end: Int = scope.lastOrNull()?.end ?: 0

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitRoot(this)
    }
}