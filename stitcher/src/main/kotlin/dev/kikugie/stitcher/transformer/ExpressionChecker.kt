package dev.kikugie.stitcher.transformer

import dev.kikugie.commons.collections.present
import dev.kikugie.stitcher.document.locate
import dev.kikugie.stitcher.issue.ProblemReporter
import dev.kikugie.stitcher.issue.ProblemSeverity
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.replacement.template.findInsertionPlaces
import dev.kikugie.stitcher.token.SYNTAX_GROUP
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.ExpressionToken
import dev.kikugie.stitcher.token.impl.LeafToken
import dev.kikugie.stitcher.token.impl.ScopeToken
import dev.kikugie.stitcher.util.id
import dev.kikugie.stitcher.util.text
import dev.kikugie.stitcher.util.unquote
import dev.kikugie.stonecutter.model.Identifier
import dev.kikugie.stonecutter.model.process.ProcessParameters
import kotlin.math.max

object ExpressionChecker : DefinitionToken.Visitor<Unit, ExpressionChecker.Context>, ExpressionToken.Visitor<Unit, ExpressionChecker.Context> {
    class Context(val params: ProcessParameters, val problems: ProblemReporter) {
        val knownReplacementIds: MutableSet<Identifier> = mutableSetOf()
        val activeReplacementIds: MutableSet<Identifier> = mutableSetOf()

        init {
            for (repl in params.replacements) repl.identifier?.let {
                knownReplacementIds += it
                if (repl.defaultEnabled) activeReplacementIds += it
            }
        }
    }
 
    override fun visitSwapLocal(swap: DefinitionToken.SwapLocal, ctx: Context) {
        for (it in swap.entries) it.expression?.accept(this, ctx)
    }

    override fun visitSwapOpener(swap: DefinitionToken.SwapOpener, ctx: Context) {
        val template = ctx.params.swaps[swap.id]
            ?: return ctx.problems.reportUnresolvedSwapId(swap, ctx.params.swaps.keys)
        if (swap.arguments.isEmpty()) return

        var placeholders = 0
        for (it in template.findInsertionPlaces())
            placeholders = max(it.number, placeholders)
        if (swap.arguments.size < placeholders)
            ctx.problems.reportInsufficientSwapInsertions(swap, placeholders)
    }

    override fun visitSwapCloser(swap: DefinitionToken.SwapCloser, ctx: Context) {
    }

    override fun visitConditionOpener(cond: DefinitionToken.ConditionOpener, ctx: Context) {
        cond.expression.accept(this, ctx)
    }

    override fun visitConditionExtension(cond: DefinitionToken.ConditionExtension, ctx: Context) {
        cond.expression?.accept(this, ctx)
    }

    override fun visitConditionCloser(cond: DefinitionToken.ConditionCloser, ctx: Context) {
    }

    override fun visitReplacementToggle(repl: DefinitionToken.ReplacementToggle, ctx: Context) {
        for (it in repl.entries) it.accept(::visitReplacementToggleEntry, ctx)
    }

    override fun visitReplacementLocal(repl: DefinitionToken.ReplacementLocal, ctx: Context) {
        repl.expression.accept(this, ctx)
        for (it in repl.entries) it.accept(::visitReplacementLocalEntry, ctx)
        if (repl.opener is ScopeToken.Named) {
            val id = repl.opener.identifier.text
            ctx.knownReplacementIds += id
            ctx.activeReplacementIds += id
        }
    }

    override fun visitReplacementCloser(repl: DefinitionToken.ReplacementCloser, ctx: Context) {
    }

    override fun visitGroup(group: ExpressionToken.Group, ctx: Context) {
        group.body.accept(this, ctx)
    }

    override fun visitUnary(unary: ExpressionToken.Unary, ctx: Context) {
        unary.target.accept(this, ctx)
    }

    override fun visitBinary(binary: ExpressionToken.Binary, ctx: Context) {
        binary.left.accept(this, ctx)
        binary.right.accept(this, ctx)
    }

    override fun visitConstant(constant: ExpressionToken.Constant, ctx: Context) {
        if (constant.text !in ctx.params.constants)
            ctx.problems.reportUnresolvedConstant(constant, ctx.params.constants.keys)
    }

    override fun visitAssignment(assignment: ExpressionToken.Assignment, ctx: Context) {
        if (assignment.id !in ctx.params.targets) when (val it = assignment.target) {
            null -> ctx.problems.reportUnresolvedImplicitTarget(assignment)
            else -> ctx.problems.reportUnresolvedExplicitTarget(it, ctx.params.targets.keys)
        }
    }

    fun visitReplacementLocalEntry(entry: DefinitionToken.ReplacementLocal.Entry, ctx: Context) {
        if (entry.source.unquote().isEmpty())
            ctx.problems.reportEmptyReplacementValue(entry.source)
        if (entry.target.unquote().isEmpty())
            ctx.problems.reportEmptyReplacementValue(entry.target)
    }

    fun visitReplacementToggleEntry(entry: DefinitionToken.ReplacementToggle.Entry, ctx: Context): Unit = when (entry.id) {
        !in ctx.knownReplacementIds ->
            ctx.problems.reportUnresolvedReplacementId(entry, ctx.knownReplacementIds)

        !in ctx.activeReplacementIds ->
            if (entry.op == null) ctx.activeReplacementIds += entry.id
            else ctx.problems.reportDuplicateDisabledReplacement(entry)

        else ->
            if (entry.op != null) ctx.activeReplacementIds -= entry.id
            else ctx.problems.reportDuplicateEnabledReplacement(entry)
    }
}

private fun ProblemReporter.reportUnresolvedSwapId(swap: DefinitionToken.SwapOpener, ids: Collection<Identifier>): Unit = report(
    SYNTAX_GROUP + "unresolved-swap-id"
) {
    loc = doc.locate(swap.identifier)
    label = "Unresolved swap '${swap.id}'"
    hint = "Available swaps: ${ids.present()}"
    docs = "https://stonecutter.kikugie.dev/wiki/config/params#string-swaps"
}

private fun ProblemReporter.reportInsufficientSwapInsertions(swap: DefinitionToken.SwapOpener, placeholders: Int): Unit = report(
    SYNTAX_GROUP + "insufficient-swap-insertions"
) {
    loc = doc.locate(swap.arguments.last().end)
    label = "Insufficient insertions for swap '${swap.id}'"
    info = "Substituted string requires $placeholders parameters, but only ${swap.arguments.size} were provided."
    docs = "https://stonecutter.kikugie.dev/wiki/config/params#swap-parameters"
}

private fun ProblemReporter.reportUnresolvedReplacementId(repl: DefinitionToken.ReplacementToggle.Entry, ids: Collection<Identifier>): Unit = report(
    SYNTAX_GROUP + "unresolved-replacement-id"
) {
    loc = doc.locate(repl)
    label = "Unresolved replacement '${repl.id}'"
    hint = "Available replacements: ${ids.present()}"
    docs = "https://stonecutter.kikugie.dev/wiki/config/params#replacements"
}

private fun ProblemReporter.reportDuplicateEnabledReplacement(repl: DefinitionToken.ReplacementToggle.Entry): Unit = report(
    SYNTAX_GROUP + "replacement-already-enabled"
) {
    loc = doc.locate(repl)
    label = "Replacement already enabled"
    info = "Replacement is already active, making this statement redundant"
    sev = ProblemSeverity.INFO
}

private fun ProblemReporter.reportDuplicateDisabledReplacement(repl: DefinitionToken.ReplacementToggle.Entry): Unit = report(
    SYNTAX_GROUP + "replacement-already-disabled"
) {
    loc = doc.locate(repl)
    label = "Replacement already disabled"
    info = "Replacement is already inactive, making this statement redundant"
    sev = ProblemSeverity.INFO
}

private fun ProblemReporter.reportEmptyReplacementValue(token: LeafToken): Unit = report(
    SYNTAX_GROUP + "empty-replacement-value"
) {
    loc = doc.locate(token)
    label = "Empty replacement value"
    info = "An empty string can't be located in text to be replaced."
}

private fun ProblemReporter.reportUnresolvedConstant(constant: ExpressionToken.Constant, ids: Collection<Identifier>): Unit = report(
    SYNTAX_GROUP + "unresolved-constant-id"
) {
    loc = doc.locate(constant)
    label = "Unresolved constant '${constant.text}'"
    hint = "Available constants: ${ids.present()}"
    docs = "https://stonecutter.kikugie.dev/wiki/config/params#condition-constants"
}

private fun ProblemReporter.reportUnresolvedExplicitTarget(token: LeafToken, ids: Collection<Identifier>): Unit = report(
    SYNTAX_GROUP + "invalid-target-id"
) {
    loc = doc.locate(token)
    label = "Unresolved predicate target '${token.text}'"
    hint = "Available targets: ${ids.present()}"
    docs = "https://stonecutter.kikugie.dev/wiki/config/params#condition-dependencies"
}

private fun ProblemReporter.reportUnresolvedImplicitTarget(token: StitcherToken): Unit = report(
    SYNTAX_GROUP + "invalid-target-id"
) {
    loc = doc.locate(token)
    label = "Unresolved default predicate target"
    hint = "What have you done to achieve this?"
}
