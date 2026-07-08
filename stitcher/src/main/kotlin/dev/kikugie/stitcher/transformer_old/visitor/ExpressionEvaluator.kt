package dev.kikugie.stitcher.transformer_old.visitor

import dev.kikugie.stitcher.antlr.StitcherLexer.Tokens
import dev.kikugie.stitcher.token.impl.ExpressionToken
import dev.kikugie.stitcher.transformer.TransformParameters
import dev.kikugie.stitcher.util.eval
import dev.kikugie.stitcher.util.id
import dev.kikugie.stitcher.util.text

internal class ExpressionEvaluator(val parameters: TransformParameters) : ExpressionToken.Visitor<Boolean> {
    override fun visitGroup(group: ExpressionToken.Group): Boolean = group.body.accept(this)
    override fun visitUnary(unary: ExpressionToken.Unary): Boolean = when (unary.operator.type.value) {
        Tokens.OP_NOT -> !unary.target.accept(this)
        else -> throw UnsupportedOperationException() // Checked beforehand
    }

    override fun visitBinary(binary: ExpressionToken.Binary): Boolean = when (binary.operator.type.value) {
        Tokens.OP_AND -> binary.left.accept(this) && binary.right.accept(this)
        Tokens.OP_OR -> binary.left.accept(this) || binary.right.accept(this)
        else -> throw UnsupportedOperationException() // Checked beforehand
    }

    override fun visitConstant(constant: ExpressionToken.Constant): Boolean =
        parameters.constants[constant.text]!! // Checked beforehand

    override fun visitAssignment(assignment: ExpressionToken.Assignment): Boolean {
        val value = parameters.dependencies[assignment.id]!! // Checked beforehand
        return assignment.eval(value)
    }
}