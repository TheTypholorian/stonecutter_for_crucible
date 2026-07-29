package dev.kikugie.stitcher.transformer

import dev.kikugie.stitcher.document.issue.ProblemReport.Severity
import dev.kikugie.stitcher.document.issue.SYNTAX_GROUP
import dev.kikugie.stitcher.document.issue.report
import dev.kikugie.stitcher.token.*
import dev.kikugie.stitcher.token.DefinitionToken.SwapOpener
import dev.kikugie.stitcher.transformer.ExpressionChecker.Context
import dev.kikugie.stitcher.util.findClosest
import dev.kikugie.stitcher.util.findInsertionPlaces
import dev.kikugie.stonecutter.model.Identifier
import dev.kikugie.stonecutter.model.process.ProcessParameters
import kotlin.math.max

object ExpressionChecker : BlockToken.Visitor<Unit, Context>, DefinitionToken.Visitor<Unit, Context>, ExpressionToken.Visitor<Unit, Context> {
    class Context(val vars: ProcessParameters) {
        val knownReplacementIds: MutableSet<Identifier> = HashSet()
        val activeReplacementIds: MutableSet<Identifier> = HashSet()

        init {
            for (repl in vars.replacements) repl.identifier?.let {
                knownReplacementIds += it
                if (repl.defaultEnabled) activeReplacementIds += it
            }
        }
    }

    override fun visitContent(content: BlockToken.Content, ctx: Context) {
    }

    override fun visitComment(comment: BlockToken.Comment, ctx: Context) {
    }

    override fun visitCode(code: BlockToken.Code, ctx: Context) {
        code.definition.accept(ExpressionChecker, ctx)
        for (it in code.scope) it.accept(this, ctx)
    }

    override fun visitRoot(root: BlockToken.Root, ctx: Context) {
        for (it in root.scope) it.accept(this, ctx)
    }

    override fun visitSwapLocal(swap: DefinitionToken.SwapLocal, ctx: Context) {
        for ((_, expression) in swap.entries) expression?.accept(this, ctx)
    }

    override fun visitSwapOpener(swap: SwapOpener, ctx: Context) {
        val template = ctx.vars.swaps[swap.id]
            ?: return reportUnresolvedSwapId(swap, ctx.vars.swaps.keys)
        if (swap.arguments.isEmpty()) return

        var placeholders = 0
        for (it in template.findInsertionPlaces())
            placeholders = max(it.number, placeholders)
        if (swap.arguments.size < placeholders)
            reportInsufficientSwapInsertions(swap, placeholders)
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
        for (it in repl.entries)
            it.accept(::visitReplacementLocalEntry, ctx)
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
        if (constant.text !in ctx.vars.constants)
            reportUnresolvedConstant(constant, ctx.vars.constants.keys)
    }

    override fun visitAssignment(assignment: ExpressionToken.Assignment, ctx: Context) {
        if (assignment.id !in ctx.vars.targets) when (val it = assignment.target) {
            null -> reportUnresolvedImplicitTarget(assignment.predicates)
            else -> reportUnresolvedExplicitTarget(it, ctx.vars.targets.keys)
        }
    }

    fun visitReplacementLocalEntry(entry: DefinitionToken.ReplacementLocal.Entry, ctx: Context) {
        if (entry.source.unquote().isEmpty())
            reportEmptyReplacementValue(entry.source)
        if (entry.target.unquote().isEmpty())
            reportEmptyReplacementValue(entry.target)
    }

    fun visitReplacementToggleEntry(entry: DefinitionToken.ReplacementToggle.Entry, ctx: Context): Unit = when (val id = entry.id) {
        !in ctx.knownReplacementIds ->
            reportUnresolvedReplacementId(entry, ctx.knownReplacementIds)
        else if (entry.op == null) ->
            if (ctx.activeReplacementIds.add(id)) Unit
            else reportDuplicateDisabledReplacement(entry)
        else ->
            if (ctx.activeReplacementIds.remove(id)) Unit
            else reportDuplicateEnabledReplacement(entry)
    }
}

private fun reportUnresolvedSwapId(swap: SwapOpener, ids: Collection<Identifier>): Unit = report(
    SYNTAX_GROUP + "unresolved-swap-id"
) {
    at = swap.identifier.ref

    label = "Unresolved swap '${swap.id}'"
    hint = buildString {
        val candidate = swap.id.findClosest(ids, 1)
        if (candidate != null) appendLine("Did you mean '$candidate'?")
        if (ids.isNotEmpty()) append("Available swaps: [${ids.joinToString()}]")
    }
    docs = "https://stonecutter.kikugie.dev/wiki/config/params#string-swaps"
}

private fun reportInsufficientSwapInsertions(swap: SwapOpener, placeholders: Int): Unit = report(
    SYNTAX_GROUP + "insufficient-swap-insertions"
) {
    at = swap.arguments.last().ref.run { copy(start = end) }

    label = "Insufficient insertions for swap '${swap.id}'"
    info = "Substituted string requires $placeholders parameters, but only ${swap.arguments.size} were provided."
    docs = "https://stonecutter.kikugie.dev/wiki/config/params#swap-parameters"
}

private fun reportUnresolvedReplacementId(repl: DefinitionToken.ReplacementToggle.Entry, ids: Collection<Identifier>): Unit = report(
    SYNTAX_GROUP + "unresolved-replacement-id"
) {
    at = repl.identifier.ref

    label = "Unresolved replacement '${repl.id}'"
    hint = buildString {
        val candidate = repl.id.findClosest(ids, 1)
        if (candidate != null) appendLine("Did you mean '$candidate'?")
        if (ids.isNotEmpty()) append("Available replacements: [${ids.joinToString()}]")
    }
    docs = "https://stonecutter.kikugie.dev/wiki/config/params#replacements"
}

private fun reportDuplicateEnabledReplacement(repl: DefinitionToken.ReplacementToggle.Entry): Unit = report(
    SYNTAX_GROUP + "replacement-already-enabled"
) {
    at = repl.identifier.ref
    severity = Severity.INFO

    label = "Replacement already enabled"
    info = "Replacement is already active, making this statement redundant"
}

private fun reportDuplicateDisabledReplacement(repl: DefinitionToken.ReplacementToggle.Entry): Unit = report(
    SYNTAX_GROUP + "replacement-already-disabled"
) {
    at = repl.identifier.ref
    severity = Severity.INFO

    label = "Replacement already disabled"
    info = "Replacement is already inactive, making this statement redundant"
}

private fun reportEmptyReplacementValue(token: LeafToken): Unit = report(
    SYNTAX_GROUP + "empty-replacement-value"
) {
    at = token.ref

    label = "Empty replacement value"
    info = "An empty string can't be located in text to be replaced."
}

private fun reportUnresolvedConstant(constant: ExpressionToken.Constant, ids: Collection<Identifier>): Unit = report(
    SYNTAX_GROUP + "unresolved-constant-id"
) {
    at = constant.value.ref

    label = "Unresolved constant '${constant.text}'"
    hint = buildString {
        val candidate = constant.text.findClosest(ids, 1)
        if (candidate != null) appendLine("Did you mean '$candidate'?")
        if (ids.isNotEmpty()) append("Available constants: [${ids.joinToString()}]")
    }
    docs = "https://stonecutter.kikugie.dev/wiki/config/params#condition-constants"
}

private fun reportUnresolvedExplicitTarget(token: LeafToken, ids: Collection<Identifier>): Unit = report(
    SYNTAX_GROUP + "invalid-target-id"
) {
    at = token.ref

    label = "Unresolved predicate target '${token.text}'"
    hint = buildString {
        val candidate = token.text.findClosest(ids, 1)
        if (candidate != null) appendLine("Did you mean '$candidate'?")
        if (ids.isNotEmpty()) append("Available targets: [${ids.joinToString()}]")
    }
    docs = "https://stonecutter.kikugie.dev/wiki/config/params#condition-dependencies"
}

private fun reportUnresolvedImplicitTarget(predicates: List<PredicateToken>): Unit = report(
    SYNTAX_GROUP + "invalid-target-id"
) {
    at = predicates.first().run { comp ?: target }.ref

    label = "Unresolved default predicate target"
    hint = "What have you done to achieve this?"
}
