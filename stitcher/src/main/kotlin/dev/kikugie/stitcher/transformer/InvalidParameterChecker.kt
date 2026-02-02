package dev.kikugie.stitcher.transformer

import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.parser.SYNTAX_GROUP
import dev.kikugie.stitcher.replacement.template.findInsertionPlaces
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.ExpressionToken
import dev.kikugie.stitcher.token.length

internal class InvalidParameterChecker(val parameters: TransformParameters, val problems: FileProblemReporter) :
    BlockToken.Visitor<Boolean>, DefinitionToken.Visitor<Boolean>, ExpressionToken.Visitor<Boolean> {
    override fun visitContent(content: BlockToken.Content) = true
    override fun visitComment(comment: BlockToken.Comment) = true

    override fun visitRoot(root: BlockToken.Root) =
        root.scope.allExhaustive { it.accept(this) }

    override fun visitCode(code: BlockToken.Code): Boolean {
        val isDefinitionValid = code.definition.accept(this)
        val isScopeValid = code.scope.allExhaustive {  it.accept(this)  }
        return isDefinitionValid && isScopeValid
    }

    override fun visitSwap(swap: DefinitionToken.Swap): Boolean {
        if (swap !is DefinitionToken.SwapOpener) return true
        val insertion = parameters.swaps[swap.identifier.text] ?: problems.report(SYNTAX_GROUP.child("invalid-parameter"), swap.identifier) {
            label = "Unresolved swap '${swap.identifier.text}'"
            documentation = "https://stonecutter.kikugie.dev/wiki/config/params#string-swaps"
        }.let { return false }
        val insertions = insertion.findInsertionPlaces().maxOfOrNull { it.number } ?: 0
        val hasEnoughParameters = swap.arguments.size >= insertions
        if (!hasEnoughParameters) problems.report(SYNTAX_GROUP.child("insufficient-insertions"), swap.arguments.first()) {
            label = "Insufficient insertions for swap '${swap.identifier.text}'"
            details = "The substituted string requires $insertions parameters, but only ${swap.arguments.size} were provided."
        }
        return hasEnoughParameters
    }

    override fun visitCondition(cond: DefinitionToken.Condition) =
        cond.expression?.accept(this) ?: true

    override fun visitReplacement(repl: DefinitionToken.Replacement) = repl.expressions.allExhaustive { entry ->
        val id = entry.identifier
        val isPresent = parameters.replacements.any { it.identifier == id.text }
        if (!isPresent) problems.report(SYNTAX_GROUP.child("invalid-parameter"), id) {
            label = "Unresolved replacement '${id.text}'"
            documentation = "https://stonecutter.kikugie.dev/wiki/config/params#replacements"
        }
        isPresent
    }

    override fun visitGroup(group: ExpressionToken.Group) =
        group.body.accept(this)

    override fun visitUnary(unary: ExpressionToken.Unary) =
        unary.target.accept(this)

    override fun visitBinary(binary: ExpressionToken.Binary): Boolean {
        val isLeftValid = binary.left.accept(this)
        val isRightValid = binary.right.accept(this)
        return isLeftValid && isRightValid
    }

    override fun visitConstant(constant: ExpressionToken.Constant): Boolean {
        val isPresent = constant.value.text in parameters.constants
        if (!isPresent) problems.report(SYNTAX_GROUP.child("invalid-parameter"), constant) {
            label = "Unresolved constant '${constant.value.text}'"
        }
        return isPresent
    }

    override fun visitAssignment(assignment: ExpressionToken.Assignment): Boolean {
        val version = parameters.dependencies[assignment.target?.text.orEmpty()]
        if (version == null) assignment.run {
            val isImplicit = target == null
            val location = if (isImplicit) predicates.first().start else target.start
            val length = if (isImplicit) predicates.last().end - location else target.length
            problems.report(SYNTAX_GROUP.child("invalid-parameter"), location, length) {
                label = if (isImplicit) "No default dependency specified"
                else "Unresolved dependency '${target.text}'"
            }
        }
        return version != null
    }
}

private inline fun <T> Iterable<T>.allExhaustive(predicate: (T) -> Boolean): Boolean =
    fold(true) { res, it -> predicate(it) && res }
