package dev.kikugie.stitcher.parser

import dev.kikugie.antlr.tree.CParserRuleContext
import dev.kikugie.stitcher.antlr.StitcherBaseContextualVisitor
import dev.kikugie.stitcher.antlr.StitcherParser
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.token.*

@Suppress("NOTHING_TO_INLINE")
object StitcherTokenBuilder : StitcherBaseContextualVisitor<StitcherToken, Document>() {
    override fun visitConditionDefinition(rule: StitcherParser.ConditionDefinitionContext, ctx: Document): StitcherToken =
        rule.condition.build<DefinitionToken.Condition>(ctx)

    override fun visitSwapDefinition(rule: StitcherParser.SwapDefinitionContext, ctx: Document): StitcherToken =
        rule.swap.build<DefinitionToken.Swap>(ctx)

    override fun visitReplacementDefinition(rule: StitcherParser.ReplacementDefinitionContext, ctx: Document): StitcherToken =
        rule.replacement.build<DefinitionToken.Replacement>(ctx)

    override fun visitOpenerCondition(rule: StitcherParser.OpenerConditionContext, ctx: Document): StitcherToken {
        val sugar: List<LeafToken> = listOfNotNull(rule.sugarIf?.toLeaf(ctx))
        val expression: ExpressionToken = rule.conditionExpression.build(ctx)
        val opener: ScopeToken? = rule.scopeOpener?.build(ctx)
        return DefinitionToken.ConditionOpener(sugar, expression, opener)
    }

    override fun visitExtensionCondition(rule: StitcherParser.ExtensionConditionContext, ctx: Document): StitcherToken {
        val closer: LeafToken? = rule.scopeClose?.toLeaf(ctx)
        val sugar: List<LeafToken> = listOfElseSugar(rule.elseSugar, ctx)
        val expression: ExpressionToken = rule.conditionExpression.build(ctx)
        val opener: ScopeToken? = rule.scopeOpener?.build(ctx)
        return DefinitionToken.ConditionExtension(closer, sugar, expression, opener)
    }

    override fun visitElseExtCondition(rule: StitcherParser.ElseExtConditionContext, ctx: Document): StitcherToken {
        val closer: LeafToken? = rule.scopeClose?.toLeaf(ctx)
        val sugar: List<LeafToken> = listOf(rule.sugarElse.toLeaf(ctx))
        val opener: ScopeToken? = rule.scopeOpener?.build(ctx)
        return DefinitionToken.ConditionExtension(closer, sugar, null, opener)
    }

    override fun visitCloserCondition(rule: StitcherParser.CloserConditionContext, ctx: Document): StitcherToken {
        val closer: LeafToken = rule.scopeClose.toLeaf(ctx)
        return DefinitionToken.ConditionCloser(closer)
    }

    override fun visitIdentifierSwap(rule: StitcherParser.IdentifierSwapContext, ctx: Document): StitcherToken {
        val identifier: LeafToken = rule.identifier.toLeaf(ctx)
        val arguments: List<LeafToken> = rule.swapArguments?.literal?.map { it.start.toLeaf(ctx) }.orEmpty()
        val opener: ScopeToken? = rule.scopeOpener?.build(ctx)
        return DefinitionToken.SwapOpener(identifier, arguments, opener)
    }

    override fun visitLocalSwap(rule: StitcherParser.LocalSwapContext, ctx: Document): StitcherToken {
        val expressions: List<DefinitionToken.SwapLocal.Entry> = buildList {
            add(rule.swapPrimary.build(ctx))
            for (it in rule.swapExtension) add(it.build(ctx))
            add(rule.swapFallback.build(ctx))
        }
        val opener: ScopeToken? = rule.scopeOpener?.build(ctx)
        return DefinitionToken.SwapLocal(expressions, opener)
    }

    override fun visitCloserSwap(rule: StitcherParser.CloserSwapContext, ctx: Document): StitcherToken {
        val closer: LeafToken = rule.scopeClose.toLeaf(ctx)
        return DefinitionToken.SwapCloser(closer)
    }

    override fun visitToggleReplacement(rule: StitcherParser.ToggleReplacementContext, ctx: Document): StitcherToken {
        val entries: List<DefinitionToken.ReplacementToggle.Entry> = rule.replacementToggle.map { it.build(ctx) }
        return DefinitionToken.ReplacementToggle(entries)
    }

    override fun visitLocalReplacement(rule: StitcherParser.LocalReplacementContext, ctx: Document): StitcherToken {
        val sugar: LeafToken = rule.sugarIf.toLeaf(ctx)
        val expression: ExpressionToken = rule.conditionExpression.build(ctx)
        val entries: List<DefinitionToken.ReplacementLocal.Entry> = rule.replacementEntry.map { it.build(ctx) }
        val opener: ScopeToken? = rule.scopeOpener?.build(ctx) ?: rule.scopeNamed?.build(ctx)
        return DefinitionToken.ReplacementLocal(sugar, expression, entries, opener)
    }

    override fun visitCloserReplacement(rule: StitcherParser.CloserReplacementContext, ctx: Document): StitcherToken {
        val closer: LeafToken = rule.scopeClose.toLeaf(ctx)
        return DefinitionToken.ReplacementCloser(closer)
    }

    override fun visitGroupExpression(rule: StitcherParser.GroupExpressionContext, ctx: Document): StitcherToken {
        val lb: LeafToken = rule.leftBrace.toLeaf(ctx)
        val rb: LeafToken = rule.rightBrace.toLeaf(ctx)
        val body: ExpressionToken = rule.conditionExpression.build(ctx)
        return ExpressionToken.Group(lb, body, rb)
    }

    override fun visitBinaryExpression(rule: StitcherParser.BinaryExpressionContext, ctx: Document): StitcherToken {
        val (left: ExpressionToken, right: ExpressionToken) = rule.conditionExpression.map { it.build<ExpressionToken>(ctx) }
        val operator: LeafToken = rule.op.toLeaf(ctx)
        return ExpressionToken.Binary(left, operator, right)
    }

    override fun visitUnaryExpression(rule: StitcherParser.UnaryExpressionContext, ctx: Document): StitcherToken {
        val operator: LeafToken = rule.opNot.toLeaf(ctx)
        val body: ExpressionToken = rule.conditionExpression.build(ctx)
        return ExpressionToken.Unary(operator, body)
    }

    override fun visitConstantExpression(rule: StitcherParser.ConstantExpressionContext, ctx: Document): StitcherToken {
        val identifier: LeafToken = rule.identifier.toLeaf(ctx)
        return ExpressionToken.Constant(identifier)
    }

    override fun visitAssignmentExpression(rule: StitcherParser.AssignmentExpressionContext, ctx: Document): StitcherToken {
        val identifier: LeafToken? = rule.identifier?.toLeaf(ctx)
        val operator: LeafToken? = rule.opAssign?.toLeaf(ctx)
        val predicates: List<PredicateToken> = rule.versionPredicate.map { it.build(ctx) }
        return ExpressionToken.Assignment(identifier, operator, predicates)
    }

    override fun visitSemanticPredicate(rule: StitcherParser.SemanticPredicateContext, ctx: Document): StitcherToken {
        val comparator: LeafToken? = (rule.semanticComparator ?: rule.stringComparator)?.start?.toLeaf(ctx)
        val version: LeafToken = rule.version.toLeaf(ctx)
        return PredicateToken(comparator, version)
    }

    override fun visitStringPredicate(rule: StitcherParser.StringPredicateContext, ctx: Document): StitcherToken {
        val comparator: LeafToken = rule.stringComparator.start.toLeaf(ctx)
        val version: LeafToken = rule.identifier.toLeaf(ctx)
        return PredicateToken(comparator, version)
    }

    override fun visitSwapPrimary(rule: StitcherParser.SwapPrimaryContext, ctx: Document): StitcherToken {
        val sugar: List<LeafToken> = listOf(rule.sugarIf.toLeaf(ctx))
        val expression: ExpressionToken = rule.conditionExpression.build(ctx)
        val literal: LeafToken = rule.literal.start.toLeaf(ctx)
        return DefinitionToken.SwapLocal.Entry(sugar, expression, literal)
    }

    override fun visitSwapExtension(rule: StitcherParser.SwapExtensionContext, ctx: Document): StitcherToken {
        val sugar: List<LeafToken> = listOfElseSugar(rule.elseSugar, ctx)
        val expression: ExpressionToken = rule.conditionExpression.build(ctx)
        val literal: LeafToken = rule.literal.start.toLeaf(ctx)
        return DefinitionToken.SwapLocal.Entry(sugar, expression, literal)
    }

    override fun visitSwapFallback(rule: StitcherParser.SwapFallbackContext, ctx: Document): StitcherToken {
        val sugar: List<LeafToken> = listOf(rule.sugarElse.toLeaf(ctx))
        val literal: LeafToken = rule.literal.start.toLeaf(ctx)
        return DefinitionToken.SwapLocal.Entry(sugar, null, literal)
    }

    override fun visitReplacementToggle(rule: StitcherParser.ReplacementToggleContext, ctx: Document): StitcherToken {
        val operator: LeafToken? = rule.opNot?.toLeaf(ctx)
        val identifier: LeafToken = rule.identifier.toLeaf(ctx)
        val comma: LeafToken? = rule.comma?.toLeaf(ctx)
        return DefinitionToken.ReplacementToggle.Entry(operator, identifier, comma)
    }

    override fun visitReplacementEntry(rule: StitcherParser.ReplacementEntryContext, ctx: Document): StitcherToken {
        val (from: LeafToken, to: LeafToken) = rule.literal.map { it.start.toLeaf(ctx) }
        val arrow: LeafToken = rule.opDir.toLeaf(ctx)
        val comma: LeafToken? = rule.comma?.toLeaf(ctx)
        return DefinitionToken.ReplacementLocal.Entry(from, arrow, to, comma)
    }

    override fun visitClosedScopeOpener(rule: StitcherParser.ClosedScopeOpenerContext, ctx: Document): StitcherToken {
        val marker: LeafToken = rule.scopeOpen.toLeaf(ctx)
        return ScopeToken.Closed(marker)
    }

    override fun visitLookupScopeOpener(rule: StitcherParser.LookupScopeOpenerContext, ctx: Document): StitcherToken {
        val marker: LeafToken = rule.scopeLookup.toLeaf(ctx)
        val plus: LeafToken? = rule.plus?.toLeaf(ctx)
        val lookup: LeafToken? = rule.literal?.start?.toLeaf(ctx)
        return ScopeToken.Lookup(marker, plus, lookup)
    }

    override fun visitScopeNamed(rule: StitcherParser.ScopeNamedContext, ctx: Document): StitcherToken {
        val marker: LeafToken = rule.scopeAs.toLeaf(ctx)
        val identifier: LeafToken = rule.identifier.toLeaf(ctx)
        return ScopeToken.Named(marker, identifier)
    }

    private inline fun <reified T : StitcherToken> CParserRuleContext.build(doc: Document): T =
        accept(this@StitcherTokenBuilder, doc) as T

    private fun listOfElseSugar(rule: StitcherParser.ElseSugarContext?, ctx: Document): List<LeafToken> = when {
        rule?.children.isNullOrEmpty() -> emptyList()
        else -> listOfNotNull(rule.sugarElif?.toLeaf(ctx), rule.sugarElse?.toLeaf(ctx), rule.sugarIf?.toLeaf(ctx))
    }
}
