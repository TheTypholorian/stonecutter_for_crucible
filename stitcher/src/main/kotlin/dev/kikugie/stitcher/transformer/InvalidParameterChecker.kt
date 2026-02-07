package dev.kikugie.stitcher.transformer

import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.parser.SYNTAX_GROUP
import dev.kikugie.stitcher.replacement.Replacement
import dev.kikugie.stitcher.replacement.template.findInsertionPlaces
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.ExpressionToken
import dev.kikugie.stitcher.token.impl.LeafToken
import dev.kikugie.stitcher.token.impl.unwrapText

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

    override fun visitSwapLocal(swap: DefinitionToken.SwapLocal): Boolean =
        swap.entries.allExhaustive { it.expression?.accept(this) ?: true }

    override fun visitSwapOpener(swap: DefinitionToken.SwapOpener): Boolean {
        val insertion = parameters.swaps[swap.identifier.text] ?: problems.report(SYNTAX_GROUP.child("invalid-parameter"), swap.identifier) {
            label = "Unresolved swap '${swap.identifier.text}'"
            docs = "https://stonecutter.kikugie.dev/wiki/config/params#string-swaps"
            hint = kotlin.run {
                val ids = parameters.swaps.keys.joinToString()
                "available swaps: [$ids]"
            }
        }.let {
            return false
        }
        val insertions = insertion.findInsertionPlaces().maxOfOrNull { it.number } ?: 0
        val hasEnoughParameters = swap.arguments.size >= insertions
        if (!hasEnoughParameters) problems.report(SYNTAX_GROUP.child("insufficient-insertions"), swap.arguments.first()) {
            label = "Insufficient insertions for swap '${swap.identifier.text}'"
            details = "The substituted string requires $insertions parameters, but only ${swap.arguments.size} were provided."
        }
        return hasEnoughParameters
    }

    override fun visitSwapCloser(swap: DefinitionToken.SwapCloser): Boolean = true

    override fun visitConditionOpener(cond: DefinitionToken.ConditionOpener): Boolean =
        cond.expression.accept(this)

    override fun visitConditionExtension(cond: DefinitionToken.ConditionExtension): Boolean =
        cond.expression?.accept(this) ?: true

    override fun visitConditionCloser(cond: DefinitionToken.ConditionCloser): Boolean = true

    override fun visitReplacementToggle(repl: DefinitionToken.ReplacementToggle): Boolean = repl.entries.allExhaustive { entry ->
        val isPresent = parameters.replacements.any { it.identifier == entry.identifier.text }
        if (!isPresent) problems.report(SYNTAX_GROUP.child("invalid-parameter"), entry.identifier) {
            label = "Unresolved replacement '${entry.identifier.text}'"
            docs = "https://stonecutter.kikugie.dev/wiki/config/params#replacements"
            hint = kotlin.run {
                val ids = parameters.replacements.mapNotNull(Replacement::identifier).toSet().joinToString()
                "available replacements: [$ids]"
            }
        }
        isPresent
    }

    override fun visitReplacementLocal(repl: DefinitionToken.ReplacementLocal): Boolean {
        var valid = repl.expression.accept(this)
        fun reportEmpty(token: LeafToken) = problems.report(SYNTAX_GROUP.child("invalid-parameter"), token) {
            label = "Empty replacement value"
        }

        if (repl.source.unwrapText().isEmpty()) {
            reportEmpty(repl.source)
            valid = false
        }

        if (repl.target.unwrapText().isEmpty()) {
            reportEmpty(repl.source)
            valid = false
        }
        return valid
    }

    override fun visitReplacementCloser(repl: DefinitionToken.ReplacementCloser): Boolean = true

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
            docs = "https://stonecutter.kikugie.dev/wiki/config/params#condition-constants"
            hint = kotlin.run {
                val ids = parameters.constants.keys.joinToString()
                "available constants: [$ids]"
            }
        }
        return isPresent
    }

    override fun visitAssignment(assignment: ExpressionToken.Assignment): Boolean {
        val version = parameters.dependencies[assignment.target?.text.orEmpty()]
        if (version == null)
            if (assignment.target == null) reportImplicit(assignment)
            else reportExplicit(assignment.target)
        return version != null
    }

    private fun reportImplicit(assignment: ExpressionToken.Assignment) {
        problems.report(SYNTAX_GROUP.child("invalid-parameter"), assignment.predicates.first()) {
            label = "No default dependency specified"
            hint = "you're doing something *really* wrong with Stonecutter to achieve this..."
        }
    }

    private fun reportExplicit(target: LeafToken) {
        problems.report(SYNTAX_GROUP.child("invalid-parameter"), target) {
            label = "Unresolved dependency '${target.text}'"
            docs = "https://stonecutter.kikugie.dev/wiki/config/params#condition-dependencies"
            hint = kotlin.run {
                val ids = parameters.dependencies.keys.joinToString()
                "available dependencies: [$ids]"
            }
        }
    }
}

private inline fun <T> Iterable<T>.allExhaustive(predicate: (T) -> Boolean): Boolean =
    fold(true) { res, it -> predicate(it) && res }
