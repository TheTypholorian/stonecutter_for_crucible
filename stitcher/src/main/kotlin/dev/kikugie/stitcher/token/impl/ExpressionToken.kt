package dev.kikugie.stitcher.token.impl

import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.StitcherType

public sealed interface ExpressionToken : StitcherToken {
    public fun <T> accept(visitor: Visitor<T>): T

    public interface Visitor<T> {
        public fun visitGroup(group: Group): T
        public fun visitUnary(unary: Unary): T
        public fun visitBinary(binary: Binary): T
        public fun visitConstant(constant: Constant): T
        public fun visitAssignment(assignment: Assignment): T
    }

    public data class Group(val lb: LeafToken, val body: ExpressionToken, val rb: LeafToken) : ExpressionToken {
        override val type: StitcherType get() = StitcherType.Composite.GROUP
        override val start: Int get() = lb.start
        override val end: Int get() = rb.end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitGroup(this)
    }

    public data class Unary(val operator: LeafToken, val target: ExpressionToken) : ExpressionToken {
        override val type: StitcherType get() = StitcherType.Composite.UNARY
        override val start: Int get() = operator.start
        override val end: Int get() = target.end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitUnary(this)
    }

    public data class Binary(val left: ExpressionToken, val operator: LeafToken, val right: ExpressionToken) : ExpressionToken {
        override val type: StitcherType get() = StitcherType.Composite.BINARY
        override val start: Int get() = left.start
        override val end: Int get() = right.end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitBinary(this)
    }

    public data class Constant(val value: LeafToken) : ExpressionToken {
        override val type: StitcherType get() = StitcherType.Composite.CONSTANT
        override val start: Int get() = value.start
        override val end: Int get() = value.end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitConstant(this)
    }

    public data class Assignment(
        val target: LeafToken?,
        val operator: LeafToken?,
        val predicates: List<PredicateToken>
    ) : ExpressionToken {
        override val type: StitcherType get() = StitcherType.Composite.ASSIGNMENT
        override val start: Int = target?.start ?: predicates.first().start
        override val end: Int = predicates.last().end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitAssignment(this)
    }
}