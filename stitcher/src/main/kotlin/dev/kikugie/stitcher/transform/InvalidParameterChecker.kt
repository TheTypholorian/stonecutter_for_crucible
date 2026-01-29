package dev.kikugie.stitcher.transform

import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.parser.SYNTAX_GROUP
import dev.kikugie.stitcher.replacement.findInsertionPlaces
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.ExpressionToken
import dev.kikugie.stitcher.token.impl.LeafToken
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

    override fun visitReplacement(repl: DefinitionToken.Replacement) = getIds(repl).asIterable().allExhaustive { id ->
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

private fun InvalidParameterChecker.getIds(repl: DefinitionToken.Replacement): Sequence<LeafToken> =
    repl.expressions.asSequence().mapNotNull {
        val expr = when (it) {
            is ExpressionToken.Unary -> it.target as? ExpressionToken.Constant
            is ExpressionToken.Constant -> it
            else -> null
        }

        if (expr == null) problems.report(SYNTAX_GROUP.child("invalid-operator"), it) {
            label = "Invalid replacement expression"
        }
        expr?.value
    }

private inline fun <T> Iterable<T>.allExhaustive(predicate: (T) -> Boolean): Boolean =
    fold(true) { res, it -> predicate(it) && res }

/*
import dev.kikugie.stitcher.antlr.StitcherLexer
import dev.kikugie.stitcher.data.composite.AssignmentExpression
import dev.kikugie.stitcher.data.composite.BinaryExpression
import dev.kikugie.stitcher.data.composite.BlockToken
import dev.kikugie.stitcher.data.composite.CodeBlock
import dev.kikugie.stitcher.data.composite.CommentBlock
import dev.kikugie.stitcher.data.composite.ConditionDefinition
import dev.kikugie.stitcher.data.composite.ConstantExpression
import dev.kikugie.stitcher.data.composite.ContentBlock
import dev.kikugie.stitcher.data.composite.DefinitionToken
import dev.kikugie.stitcher.data.composite.ExpressionToken
import dev.kikugie.stitcher.data.composite.GroupExpression
import dev.kikugie.stitcher.data.composite.ReplacementDefinition
import dev.kikugie.stitcher.data.composite.RootBlock
import dev.kikugie.stitcher.data.composite.SwapDefinition
import dev.kikugie.stitcher.data.composite.UnaryExpression
import dev.kikugie.stitcher.data.eval.BlockIsBlankVisitor.isBlank
import dev.kikugie.stitcher.issue.ProblemSource
import dev.kikugie.stitcher.issue.at
import dev.kikugie.stitcher.transform.TransformParameters

internal class InvalidArgChecker(val parameters: TransformParameters, problems: ProblemSource) :
    BlockToken.Visitor<Unit>, DefinitionToken.Visitor<Unit>, ExpressionToken.Visitor<Unit>, ProblemSource by problems {
    private var canAcceptReplacements: Boolean = true

    override fun visitContent(content: ContentBlock) {
        canAcceptReplacements = canAcceptReplacements && content.isBlank()
    }

    override fun visitComment(comment: CommentBlock) = Unit

    override fun visitCode(code: CodeBlock) {
        if (code.definition is ReplacementDefinition && !canAcceptReplacements)
            at(code.marker) report "Late replacement token"

        code.definition.accept(this)
        for (it in code.scope) it.accept(this)
    }

    override fun visitRoot(root: RootBlock) {
        for (it in root.scope) it.accept(this)
    }

    override fun visitReplacement(repl: ReplacementDefinition) = Unit

    override fun visitSwap(swap: SwapDefinition) {
        canAcceptReplacements = false
        val identifier = swap.identifier?.text
        if (identifier != null && identifier !in parameters.swaps)
            at(swap.identifier!!) report "Unresolved swap identifier '$identifier'"
    }

    override fun visitCondition(cond: ConditionDefinition) {
        canAcceptReplacements = false
        cond.expression?.accept(this)
    }

    override fun visitGroup(group: GroupExpression) {
        group.body.accept(this)
    }

    override fun visitUnary(unary: UnaryExpression) {
        if (unary.operator.type.value != StitcherLexer.OP_NOT)
            at(unary.operator) report "Unsupported unary operator ${unary.operator.type.name}"
        unary.target.accept(this)
    }

    override fun visitBinary(binary: BinaryExpression) {
        if (binary.operator.type.value.let { it != StitcherLexer.OP_AND && it != StitcherLexer.OP_OR })
            at(binary.operator) report "Unsupported binary operator ${binary.operator.type.name}"
        binary.left.accept(this)
        binary.right.accept(this)
    }

    override fun visitConstant(constant: ConstantExpression) {
        val name = constant.value.text
        if (name !in parameters.constants)
            at(constant.value) report "Unresolved constant '$name'"
    }

    override fun visitAssignment(assignment: AssignmentExpression) {
        val name = assignment.target?.text.orEmpty()
        if (name !in parameters.dependencies)
            if (name.isEmpty()) at(assignment.predicates.first()) report "No default dependency specified"
            else at(assignment.target!!) report "Unresolved dependency '$name'"
    }
}
*/