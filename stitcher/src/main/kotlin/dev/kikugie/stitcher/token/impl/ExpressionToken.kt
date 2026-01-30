package dev.kikugie.stitcher.token.impl

import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.StitcherToken

sealed interface ExpressionToken : StitcherToken {
    fun <T> accept(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visitGroup(group: Group): T
        fun visitUnary(unary: Unary): T
        fun visitBinary(binary: Binary): T
        fun visitConstant(constant: Constant): T
        fun visitAssignment(assignment: Assignment): T
    }

    data class Group(val lb: LeafToken, val body: ExpressionToken, val rb: LeafToken) : ExpressionToken {
        override val type: NativeStitcherType get() = NativeStitcherType.GROUP
        override val start: Int get() = lb.start
        override val end: Int get() = rb.end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitGroup(this)
    }

    data class Unary(val operator: LeafToken, val target: ExpressionToken) : ExpressionToken {
        override val type: NativeStitcherType get() = NativeStitcherType.UNARY
        override val start: Int get() = operator.start
        override val end: Int get() = target.end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitUnary(this)
    }

    data class Binary(val left: ExpressionToken, val operator: LeafToken, val right: ExpressionToken) : ExpressionToken {
        override val type: NativeStitcherType get() = NativeStitcherType.BINARY
        override val start: Int get() = left.start
        override val end: Int get() = right.end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitBinary(this)
    }

    data class Constant(val value: LeafToken) : ExpressionToken {
        override val type: NativeStitcherType get() = NativeStitcherType.CONSTANT
        override val start: Int get() = value.start
        override val end: Int get() = value.end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitConstant(this)
    }

    data class Assignment(
        val target: LeafToken?,
        val operator: LeafToken?,
        val predicates: List<PredicateToken>
    ) : ExpressionToken {
        override val type: NativeStitcherType get() = NativeStitcherType.ASSIGNMENT
        override val start: Int = target?.start ?: predicates.first().start
        override val end: Int = predicates.last().end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitAssignment(this)
    }
}