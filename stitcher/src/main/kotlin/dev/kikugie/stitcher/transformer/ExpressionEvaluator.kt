package dev.kikugie.stitcher.transformer

import dev.kikugie.stitcher.antlr.StitcherLexer.Tokens
import dev.kikugie.stitcher.token.impl.ExpressionToken
import dev.kikugie.stitcher.util.eval
import dev.kikugie.stitcher.util.id
import dev.kikugie.stitcher.util.text
import dev.kikugie.stonecutter.model.process.ProcessParameters

object ExpressionEvaluator : ExpressionToken.Visitor<Boolean, ProcessParameters> {
    override fun visitGroup(group: ExpressionToken.Group, ctx: ProcessParameters): Boolean = group.body.accept(this, ctx)

    override fun visitUnary(unary: ExpressionToken.Unary, ctx: ProcessParameters): Boolean = when (unary.operator.type.value) {
        Tokens.OP_NOT -> !unary.target.accept(this, ctx)
        else -> throw UnsupportedOperationException() // Checked beforehand
    }

    override fun visitBinary(binary: ExpressionToken.Binary, ctx: ProcessParameters): Boolean = when (binary.operator.type.value) {
        Tokens.OP_AND -> binary.left.accept(this, ctx) && binary.right.accept(this, ctx)
        Tokens.OP_OR -> binary.left.accept(this, ctx) || binary.right.accept(this, ctx)
        else -> throw UnsupportedOperationException() // Checked beforehand
    }

    override fun visitConstant(constant: ExpressionToken.Constant, ctx: ProcessParameters): Boolean =
        checkNotNull(ctx.constants[constant.text])

    override fun visitAssignment(assignment: ExpressionToken.Assignment, ctx: ProcessParameters): Boolean =
        assignment.eval(checkNotNull(ctx.targets[assignment.id]))
}
