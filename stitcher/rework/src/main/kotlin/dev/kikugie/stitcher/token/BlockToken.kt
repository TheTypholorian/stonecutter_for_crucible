package dev.kikugie.stitcher.token

import dev.kikugie.stitcher.document.DocumentReference
import dev.kikugie.stitcher.token.type.BuiltinTokenType

sealed interface BlockToken : StitcherToken {
    val ref: DocumentReference

    fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T

    interface Visitor<T, C> {
        fun visitContent(content: Content, ctx: C): T
        fun visitComment(comment: Comment, ctx: C): T
        fun visitCode(code: Code, ctx: C): T
        fun visitRoot(root: Root, ctx: C): T
    }

    data class Content(val leaf: LeafToken) : BlockToken {
        override val type: BuiltinTokenType get() = BuiltinTokenType.CONTENT
        override val ref: DocumentReference get() = leaf.ref

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitContent(this, ctx)
    }

    data class Comment(val opener: LeafToken?, val body: LeafToken, val closer: LeafToken?) : BlockToken {
        override val type: BuiltinTokenType get() = BuiltinTokenType.COMMENT
        override val ref: DocumentReference = DocumentReference(body.ref.document, opener?.start ?: body.start, closer?.end ?: body.end)

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitComment(this, ctx)
    }

    data class Code(val host: Comment, val marker: LeafToken, val definition: DefinitionToken, val scope: List<BlockToken>) : BlockToken {
        override val type: BuiltinTokenType get() = BuiltinTokenType.CODE
        override val ref: DocumentReference get() = host.ref

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitCode(this, ctx)
    }

    data class Root(override val ref: DocumentReference, val scope: List<BlockToken>) : BlockToken {
        override val type: BuiltinTokenType get() = BuiltinTokenType.ROOT

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitRoot(this, ctx)
    }
}
