package dev.kikugie.stitcher.transform

import dev.kikugie.stitcher.antlr.StitcherLexer
import dev.kikugie.stitcher.token.impl.ExpressionToken

internal class ExpressionEvaluator(val parameters: TransformParameters) : ExpressionToken.Visitor<Boolean> {
    override fun visitGroup(group: ExpressionToken.Group): Boolean = group.body.accept(this)
    override fun visitUnary(unary: ExpressionToken.Unary): Boolean = when (unary.operator.type.value) {
        StitcherLexer.OP_NOT -> !unary.target.accept(this)
        else -> throw UnsupportedOperationException() // Checked beforehand
    }

    override fun visitBinary(binary: ExpressionToken.Binary): Boolean = when (binary.operator.type.value) {
        StitcherLexer.OP_AND -> binary.left.accept(this) && binary.right.accept(this)
        StitcherLexer.OP_OR -> binary.left.accept(this) || binary.right.accept(this)
        else -> throw UnsupportedOperationException() // Checked beforehand
    }

    override fun visitConstant(constant: ExpressionToken.Constant): Boolean =
        parameters.constants[constant.value.text]!! // Checked beforehand

    override fun visitAssignment(assignment: ExpressionToken.Assignment): Boolean {
        val value = parameters.dependencies[assignment.target?.text.orEmpty()]!! // Checked beforehand
        return assignment.predicates.all { it.predicate(value) }
    }
}