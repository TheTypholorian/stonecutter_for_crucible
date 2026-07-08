package dev.kikugie.stitcher.transformer_old.visitor

import dev.kikugie.commons.collections.present
import dev.kikugie.stitcher.document.locate
import dev.kikugie.stitcher.issue.ProblemReporter
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.issue.runMarked
import dev.kikugie.stitcher.parser.SYNTAX_GROUP
import dev.kikugie.stitcher.replacement.Replacement
import dev.kikugie.stitcher.replacement.template.findInsertionPlaces
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.ExpressionToken
import dev.kikugie.stitcher.token.impl.LeafToken
import dev.kikugie.stitcher.token.impl.ScopeToken
import dev.kikugie.stitcher.transformer.TransformParameters
import dev.kikugie.stitcher.util.id
import dev.kikugie.stitcher.util.text
import dev.kikugie.stitcher.util.unquote

class ParameterChecker(val parameters: TransformParameters, val problems: ProblemReporter) :
    BlockToken.Visitor<Unit>, DefinitionToken.Visitor<Unit>, ExpressionToken.Visitor<Unit> {
    private val replacementIds = parameters.replacements.asSequence()
        .mapNotNull(Replacement::identifier).toMutableSet()

    override fun visitContent(content: BlockToken.Content): Unit = Unit
    override fun visitComment(comment: BlockToken.Comment): Unit = Unit

    override fun visitRoot(root: BlockToken.Root) {
        for (block in root.scope) block.accept(this)
    }

    override fun visitCode(code: BlockToken.Code) {
        code.definition.accept(this)
        for (block in code.scope) block.accept(this)
    }

    override fun visitSwapLocal(swap: DefinitionToken.SwapLocal) {
        for (entry in swap.entries) entry.expression?.accept(this)
    }

    override fun visitSwapOpener(swap: DefinitionToken.SwapOpener) {
        val insertion = parameters.swaps[swap.id] ?: return problems.report(SYNTAX_GROUP + "invalid-swap-id") {
            loc = doc.locate(swap.identifier)
            label = "Unresolved swap '${swap.id}'"
            docs = "https://stonecutter.kikugie.dev/wiki/config/params#string-swaps"
            hint = "Available swaps: ${parameters.swaps.keys.present()}"
        }

        val placeholders = insertion.findInsertionPlaces().maxOfOrNull { it.number } ?: 0
        if (swap.arguments.size < placeholders) problems.report(SYNTAX_GROUP + "insufficient-swap-insertions") {
            loc = doc.locate(swap.arguments.last().end)
            label = "Insufficient insertions for swap '${swap.id}'"
            info = "The substituted string requires $placeholders parameters, but only ${swap.arguments.size} were provided."
            docs = "https://stonecutter.kikugie.dev/wiki/config/params#swap-parameters"
        }
    }

    override fun visitSwapCloser(swap: DefinitionToken.SwapCloser): Unit = Unit

    override fun visitConditionOpener(cond: DefinitionToken.ConditionOpener) {
        cond.expression.accept(this)
    }

    override fun visitConditionExtension(cond: DefinitionToken.ConditionExtension) {
        cond.expression?.accept(this)
    }

    override fun visitConditionCloser(cond: DefinitionToken.ConditionCloser): Unit = Unit

    override fun visitReplacementToggle(repl: DefinitionToken.ReplacementToggle) {
        for (entry in repl.entries) if (entry.id !in replacementIds)
            problems.report(SYNTAX_GROUP + "invalid-repl-id") {
                loc = doc.locate(entry)
                label = "Unresolved replacement '${entry.id}'"
                docs = "https://stonecutter.kikugie.dev/wiki/config/params#replacements"
                hint = "Available replacements: ${parameters.replacements.mapNotNull(Replacement::identifier).toSet().present()}"
            }
    }

    override fun visitReplacementLocal(repl: DefinitionToken.ReplacementLocal) {
        fun reportEmpty(token: LeafToken) = problems.report(SYNTAX_GROUP + "empty-repl-value") {
            loc = doc.locate(token)
            label = "Empty replacement value"
            info = "An empty string can't be located in text to be replaced."
        }

        repl.expression.accept(this)
        for (entry in repl.entries) {
            if (entry.source.unquote().isEmpty()) reportEmpty(entry.source)
            if (entry.target.unquote().isEmpty()) reportEmpty(entry.target)
        }
        // FIXME: This should have a test
        if (repl.opener is ScopeToken.Named)
            replacementIds += repl.opener.identifier.text
    }

    override fun visitReplacementCloser(repl: DefinitionToken.ReplacementCloser): Unit = Unit

    override fun visitGroup(group: ExpressionToken.Group) {
        group.body.accept(this)
    }

    override fun visitUnary(unary: ExpressionToken.Unary) {
        unary.target.accept(this)
    }

    override fun visitBinary(binary: ExpressionToken.Binary) {
        binary.left.accept(this)
        binary.right.accept(this)
    }

    override fun visitConstant(constant: ExpressionToken.Constant) {
        if (constant.text !in parameters.constants) problems.report(SYNTAX_GROUP + "invalid-constant-id") {
            loc = doc.locate(constant)
            label = "Unresolved constant '${constant.text}'"
            docs = "https://stonecutter.kikugie.dev/wiki/config/params#condition-constants"
            hint = "Available constants: ${parameters.constants.keys.present()}"
        }
    }

    override fun visitAssignment(assignment: ExpressionToken.Assignment) {
        if (assignment.id !in parameters.dependencies)
            if (assignment.target != null) reportExplicit(assignment.target)
            else reportImplicit(assignment.predicates.first())
    }

    private fun reportImplicit(predicate: StitcherToken) = problems.report(SYNTAX_GROUP + "invalid-dependency-id") {
        loc = doc.locate(predicate)
        label = "No default dependency specified"
        hint = "You're doing something *really* wrong with Stonecutter to achieve this..."
    }

    private fun reportExplicit(target: LeafToken) = problems.report(SYNTAX_GROUP + "invalid-dependency-id") {
        loc = doc.locate(target)
        label = "Unresolved dependency '${target.text}'"
        docs = "https://stonecutter.kikugie.dev/wiki/config/params#condition-dependencies"
        hint = "Available dependencies: ${parameters.dependencies.keys.present()}"
    }

    fun check(token: BlockToken): Boolean = problems.runMarked({ token.accept(this) }) { _, ok -> ok }
    fun check(token: DefinitionToken): Boolean = problems.runMarked({ token.accept(this) }) { _, ok -> ok }
    fun check(token: ExpressionToken): Boolean = problems.runMarked({ token.accept(this) }) { _, ok -> ok }
}