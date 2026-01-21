package dev.kikugie.stitcher.transform.impl

import dev.kikugie.stitcher.antlr.StitcherLexer
import dev.kikugie.stitcher.issue.ProblemID
import dev.kikugie.stitcher.issue.ProblemReporter
import dev.kikugie.stitcher.issue.bail
import dev.kikugie.stitcher.token.impl.ExpressionToken
import dev.kikugie.stitcher.token.length
import dev.kikugie.stitcher.transform.TransformParameters

private val INVALID_OPERATOR: ProblemID = ProblemID.SYNTAX_GROUP.create("invalid-operator")
private val INVALID_PARAMETER: ProblemID = ProblemID.SYNTAX_GROUP.create("invalid-parameter")

internal class ExpressionEvaluator(val parameters: TransformParameters, val problems: ProblemReporter) : ExpressionToken.Visitor<Boolean> {
    override fun visitGroup(group: ExpressionToken.Group): Boolean = group.body.accept(this)
    override fun visitUnary(unary: ExpressionToken.Unary): Boolean = when (unary.operator.type.value) {
        StitcherLexer.OP_NOT -> !unary.target.accept(this)
        else -> problems.bail(INVALID_OPERATOR, unary.operator) {
            label = "Invalid unary operator ${unary.operator.type.name}"
        }
    }

    override fun visitBinary(binary: ExpressionToken.Binary): Boolean = when (binary.operator.type.value) {
        StitcherLexer.OP_AND -> binary.left.accept(this) && binary.right.accept(this)
        StitcherLexer.OP_OR -> binary.left.accept(this) || binary.right.accept(this)
        else -> problems.bail(INVALID_OPERATOR, binary.operator) {
            label = "Invalid unary operator ${binary.operator.type.name}"
        }
    }

    override fun visitConstant(constant: ExpressionToken.Constant): Boolean {
        val value = parameters.constants[constant.value.text]
        return value ?: problems.bail(INVALID_PARAMETER, constant) {
            label = "Unresolved constant '${constant.value.text}'"
        }
    }

    override fun visitAssignment(assignment: ExpressionToken.Assignment): Boolean {
        val value = parameters.dependencies[assignment.target?.text.orEmpty()] ?: assignment.run {
            val isImplicit = target == null
            val location = if (isImplicit) predicates.first().start else target.start
            val length = if (isImplicit) predicates.last().end - location else target.length
            problems.bail(INVALID_PARAMETER, location, length) {
                label = if (isImplicit) "No default dependency specified"
                else "Unresolved dependency '${target.text}'"
            }
        }
        return assignment.predicates.all { it.predicate(value) }
    }
}