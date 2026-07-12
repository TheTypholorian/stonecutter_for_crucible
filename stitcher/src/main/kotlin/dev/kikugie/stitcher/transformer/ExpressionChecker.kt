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

class ExpressionChecker(val params: ProcessParameters) : DefinitionToken.Visitor<Unit, ProblemReporter>, ExpressionToken.Visitor<Unit, ProblemReporter> {
    val knownReplacementIds: MutableSet<Identifier> = mutableSetOf()
    val activeReplacementIds: MutableSet<Identifier> = mutableSetOf()

    init {
        for (repl in params.replacements) repl.identifier?.let {
            knownReplacementIds += it
            if (repl.defaultEnabled) activeReplacementIds += it
        }
    }
 
    override fun visitSwapLocal(swap: DefinitionToken.SwapLocal, ctx: ProblemReporter) {
        for (it in swap.entries) it.expression?.accept(this, ctx)
    }

    override fun visitSwapOpener(swap: DefinitionToken.SwapOpener, ctx: ProblemReporter) {
        val template = params.swaps[swap.id]
            ?: return ctx.reportUnresolvedSwapId(swap, params.swaps.keys)
        if (swap.arguments.isEmpty()) return

        var placeholders = 0
        for (it in template.findInsertionPlaces())
            placeholders = max(it.number, placeholders)
        if (swap.arguments.size < placeholders)
            ctx.reportInsufficientSwapInsertions(swap, placeholders)
    }

    override fun visitSwapCloser(swap: DefinitionToken.SwapCloser, ctx: ProblemReporter) {
    }

    override fun visitConditionOpener(cond: DefinitionToken.ConditionOpener, ctx: ProblemReporter) {
        cond.expression.accept(this, ctx)
    }

    override fun visitConditionExtension(cond: DefinitionToken.ConditionExtension, ctx: ProblemReporter) {
        cond.expression?.accept(this, ctx)
    }

    override fun visitConditionCloser(cond: DefinitionToken.ConditionCloser, ctx: ProblemReporter) {
    }

    override fun visitReplacementToggle(repl: DefinitionToken.ReplacementToggle, ctx: ProblemReporter) {
        for (it in repl.entries) it.accept(::visitReplacementToggleEntry, ctx)
    }

    override fun visitReplacementLocal(repl: DefinitionToken.ReplacementLocal, ctx: ProblemReporter) {
        repl.expression.accept(this, ctx)
        for (it in repl.entries) it.accept(::visitReplacementLocalEntry, ctx)
        if (repl.opener is ScopeToken.Named) {
            val id = repl.opener.identifier.text
            knownReplacementIds += id
            activeReplacementIds += id
        }
    }

    override fun visitReplacementCloser(repl: DefinitionToken.ReplacementCloser, ctx: ProblemReporter) {
    }

    override fun visitGroup(group: ExpressionToken.Group, ctx: ProblemReporter) {
        group.body.accept(this, ctx)
    }

    override fun visitUnary(unary: ExpressionToken.Unary, ctx: ProblemReporter) {
        unary.target.accept(this, ctx)
    }

    override fun visitBinary(binary: ExpressionToken.Binary, ctx: ProblemReporter) {
        binary.left.accept(this, ctx)
        binary.right.accept(this, ctx)
    }

    override fun visitConstant(constant: ExpressionToken.Constant, ctx: ProblemReporter) {
        if (constant.text !in params.constants)
            ctx.reportUnresolvedConstant(constant, params.constants.keys)
    }

    override fun visitAssignment(assignment: ExpressionToken.Assignment, ctx: ProblemReporter) {
        if (assignment.id !in params.targets) when (val it = assignment.target) {
            null -> ctx.reportUnresolvedImplicitTarget(assignment)
            else -> ctx.reportUnresolvedExplicitTarget(it, params.targets.keys)
        }
    }

    fun visitReplacementLocalEntry(entry: DefinitionToken.ReplacementLocal.Entry, ctx: ProblemReporter) {
        if (entry.source.unquote().isEmpty())
            ctx.reportEmptyReplacementValue(entry.source)
        if (entry.target.unquote().isEmpty())
            ctx.reportEmptyReplacementValue(entry.target)
    }

    fun visitReplacementToggleEntry(entry: DefinitionToken.ReplacementToggle.Entry, ctx: ProblemReporter): Unit = when (entry.id) {
        !in knownReplacementIds ->
            ctx.reportUnresolvedReplacementId(entry, knownReplacementIds)

        !in activeReplacementIds ->
            if (entry.op == null) activeReplacementIds += entry.id
            else ctx.reportDuplicateDisabledReplacement(entry)

        else ->
            if (entry.op != null) activeReplacementIds -= entry.id
            else ctx.reportDuplicateEnabledReplacement(entry)
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
