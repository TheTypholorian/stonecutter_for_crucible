package dev.kikugie.stitcher.token

import dev.kikugie.stitcher.token.type.BuiltinTokenType

sealed interface ExpressionToken : StitcherToken {
    fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T

    interface Visitor<T, C> {
        fun visitGroup(group: Group, ctx: C): T
        fun visitUnary(unary: Unary, ctx: C): T
        fun visitBinary(binary: Binary, ctx: C): T
        fun visitConstant(constant: Constant, ctx: C): T
        fun visitAssignment(assignment: Assignment, ctx: C): T
    }

    data class Group(val lb: LeafToken, val body: ExpressionToken, val rb: LeafToken) : ExpressionToken {
        override val type: BuiltinTokenType get() = BuiltinTokenType.GROUP

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitGroup(this, ctx)
    }

    data class Unary(val operator: LeafToken, val target: ExpressionToken) : ExpressionToken {
        override val type: BuiltinTokenType get() = BuiltinTokenType.UNARY

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitUnary(this, ctx)
    }

    data class Binary(val left: ExpressionToken, val operator: LeafToken, val right: ExpressionToken) : ExpressionToken {
        override val type: BuiltinTokenType get() = BuiltinTokenType.BINARY

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitBinary(this, ctx)
    }

    data class Constant(val value: LeafToken) : ExpressionToken {
        override val type: BuiltinTokenType get() = BuiltinTokenType.CONSTANT

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitConstant(this, ctx)
    }

    data class Assignment(
        val target: LeafToken?,
        val operator: LeafToken?,
        val predicates: List<PredicateToken>
    ) : ExpressionToken {
        override val type: BuiltinTokenType get() = BuiltinTokenType.ASSIGNMENT

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitAssignment(this, ctx)
    }
}
