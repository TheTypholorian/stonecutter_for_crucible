package dev.kikugie.stitcher.parser

import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.semver.data.StringVersion
import dev.kikugie.semver.data.VersionOperator
import dev.kikugie.semver.data.VersionPredicate
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.StitcherBaseContextualVisitor
import dev.kikugie.stitcher.antlr.StitcherLexer.Tokens
import dev.kikugie.stitcher.antlr.StitcherParser
import dev.kikugie.stitcher.antlr.acceptAs
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.impl.*
import dev.kikugie.stitcher.token.toLeafToken

internal class TokenBuilder : StitcherBaseContextualVisitor<StitcherToken, Document>() {
    override fun visitConditionDefinition(rule: StitcherParser.ConditionDefinitionContext, ctx: Document): StitcherToken =
        CodeToken(rule.condMark.toLeafToken(ctx), rule.condition.acceptAs(this, ctx))

    override fun visitSwapDefinition(rule: StitcherParser.SwapDefinitionContext, ctx: Document): StitcherToken =
        CodeToken(rule.swapMark.toLeafToken(ctx), rule.swap.acceptAs(this, ctx))

    override fun visitReplacementDefinition(rule: StitcherParser.ReplacementDefinitionContext, ctx: Document): StitcherToken =
        CodeToken(rule.replMark.toLeafToken(ctx), rule.replacement.acceptAs(this, ctx))

    override fun visitOpenerCondition(rule: StitcherParser.OpenerConditionContext, ctx: Document): StitcherToken {
        val sugar = listOfNotNull(rule.sugarIf?.toLeafToken(ctx))
        val expr = rule.conditionExpression.acceptAs<ExpressionToken, _>(this, ctx)
        val opener = rule.scopeOpener?.acceptAs<ScopeToken, _>(this, ctx)
        return DefinitionToken.ConditionOpener(sugar, expr, opener)
    }

    override fun visitExtensionCondition(rule: StitcherParser.ExtensionConditionContext, ctx: Document): StitcherToken {
        val closer = rule.scopeClose?.toLeafToken(ctx)
        val sugar = collectElseSugar(rule.elseSugar, ctx)
        val expr = rule.conditionExpression.acceptAs<ExpressionToken, _>(this, ctx)
        val opener = rule.scopeOpener?.acceptAs<ScopeToken, _>(this, ctx)
        return DefinitionToken.ConditionExtension(closer, sugar, expr, opener)
    }

    override fun visitElseExtCondition(rule: StitcherParser.ElseExtConditionContext, ctx: Document): StitcherToken {
        val closer = rule.scopeClose?.toLeafToken(ctx)
        val sugar = listOf(rule.sugarElse.toLeafToken(ctx))
        val opener = rule.scopeOpener?.acceptAs<ScopeToken, _>(this, ctx)
        return DefinitionToken.ConditionExtension(closer, sugar, null, opener)
    }

    override fun visitCloserCondition(rule: StitcherParser.CloserConditionContext, ctx: Document): StitcherToken {
        val closer = rule.scopeClose.toLeafToken(ctx)
        return DefinitionToken.ConditionCloser(closer)
    }

    override fun visitIdentifierSwap(rule: StitcherParser.IdentifierSwapContext, ctx: Document): StitcherToken {
        val id = rule.identifier.toLeafToken(ctx)
        val args = rule.swapArguments?.literal?.map { it.acceptAs<LeafToken, _>(this, ctx) }.orEmpty()
        val opener = rule.scopeOpener?.acceptAs<ScopeToken, _>(this, ctx)
        return DefinitionToken.SwapOpener(id, args, opener)
    }

    override fun visitLocalSwap(rule: StitcherParser.LocalSwapContext, ctx: Document): StitcherToken {
        val expr = buildList<DefinitionToken.SwapLocal.Entry> {
            add(rule.swapPrimary.acceptAs(this@TokenBuilder, ctx))
            for (it in rule.swapExtension)
                add(it.acceptAs(this@TokenBuilder, ctx))
            add(rule.swapFallback.acceptAs(this@TokenBuilder, ctx))
        }
        val opener = rule.scopeOpener?.acceptAs<ScopeToken, _>(this, ctx)
        return DefinitionToken.SwapLocal(expr, opener)
    }

    override fun visitCloserSwap(rule: StitcherParser.CloserSwapContext, ctx: Document): StitcherToken {
        val closer = rule.scopeClose.toLeafToken(ctx)
        return DefinitionToken.SwapCloser(closer)
    }

    override fun visitToggleReplacement(rule: StitcherParser.ToggleReplacementContext, ctx: Document): StitcherToken {
        val entries = rule.replacementToggle.map { it.acceptAs<DefinitionToken.ReplacementToggle.Entry, _>(this, ctx) }
        return DefinitionToken.ReplacementToggle(entries)
    }

    override fun visitLocalReplacement(rule: StitcherParser.LocalReplacementContext, ctx: Document): StitcherToken {
        val sugar = rule.sugarIf.toLeafToken(ctx)
        val expr = rule.conditionExpression.acceptAs<ExpressionToken, _>(this, ctx)
        val entries = rule.replacementEntry.map { it.acceptAs<DefinitionToken.ReplacementLocal.Entry, _>(this, ctx) }
        val opener = rule.scopeOpener?.acceptAs<ScopeToken, _>(this, ctx)
        return DefinitionToken.ReplacementLocal(sugar, expr, entries, opener)
    }

    override fun visitCloserReplacement(rule: StitcherParser.CloserReplacementContext, ctx: Document): StitcherToken {
        val closer = rule.scopeClose.toLeafToken(ctx)
        return DefinitionToken.ReplacementCloser(closer)
    }

    override fun visitGroupExpression(rule: StitcherParser.GroupExpressionContext, ctx: Document): StitcherToken {
        val lb = rule.leftBrace.toLeafToken(ctx)
        val rb = rule.rightBrace.toLeafToken(ctx)
        val body = rule.conditionExpression.acceptAs<ExpressionToken, _>(this, ctx)
        return ExpressionToken.Group(lb, body, rb)
    }

    override fun visitBinaryExpression(rule: StitcherParser.BinaryExpressionContext, ctx: Document): StitcherToken {
        val (left, right) = rule.conditionExpression.map { it.acceptAs<ExpressionToken, _>(this, ctx) }
        val op = rule.op.toLeafToken(ctx)
        return ExpressionToken.Binary(left, op, right)
    }

    override fun visitUnaryExpression(rule: StitcherParser.UnaryExpressionContext, ctx: Document): StitcherToken {
        val op = rule.opNot.toLeafToken(ctx)
        val expr = rule.conditionExpression.acceptAs<ExpressionToken, _>(this, ctx)
        return ExpressionToken.Unary(op, expr)
    }

    override fun visitConstantExpression(rule: StitcherParser.ConstantExpressionContext, ctx: Document): StitcherToken {
        val id = rule.identifier.toLeafToken(ctx)
        return ExpressionToken.Constant(id)
    }

    override fun visitAssignmentExpression(rule: StitcherParser.AssignmentExpressionContext, ctx: Document): StitcherToken {
        val id = rule.identifier?.toLeafToken(ctx)
        val op = rule.opAssign?.toLeafToken(ctx)
        val prs = rule.versionPredicate.map { it.acceptAs<PredicateToken, _>(this, ctx) }
        return ExpressionToken.Assignment(id, op, prs)
    }

    override fun visitSemanticPredicate(rule: StitcherParser.SemanticPredicateContext, ctx: Document): StitcherToken {
        val comp = (rule.semanticComparator ?: rule.stringComparator)?.acceptAs<LeafToken, _>(this, ctx)
        val vers = rule.semanticVersion.acceptAs<LeafToken, _>(this, ctx)
        return PredicateToken(comp, vers, VersionPredicate(matchPredicateType(comp), buildSemanticVersion(rule.semanticVersion)))
    }

    override fun visitStringPredicate(rule: StitcherParser.StringPredicateContext, ctx: Document): StitcherToken {
        val comp = rule.stringComparator.acceptAs<LeafToken, _>(this, ctx)
        val vers = rule.stringVersion.acceptAs<LeafToken, _>(this, ctx)
        return PredicateToken(comp, vers, VersionPredicate(matchPredicateType(comp), buildStringVersion(rule.stringVersion)))
    }

    override fun visitSemanticComparator(rule: StitcherParser.SemanticComparatorContext, ctx: Document): StitcherToken =
        rule.toLeafToken(ctx, NativeStitcherType.OPERATOR)

    override fun visitStringComparator(rule: StitcherParser.StringComparatorContext, ctx: Document): StitcherToken =
        rule.toLeafToken(ctx, NativeStitcherType.OPERATOR)

    override fun visitSemanticVersion(rule: StitcherParser.SemanticVersionContext, ctx: Document): StitcherToken =
        rule.toLeafToken(ctx, NativeStitcherType.VERSION)

    override fun visitStringVersion(rule: StitcherParser.StringVersionContext, ctx: Document): StitcherToken =
        rule.toLeafToken(ctx, NativeStitcherType.VERSION)

    override fun visitClosedScopeOpener(rule: StitcherParser.ClosedScopeOpenerContext, ctx: Document): StitcherToken {
        val marker = rule.scopeOpen.toLeafToken(ctx)
        return ScopeToken.Closed(marker)
    }

    override fun visitLookupScopeOpener(rule: StitcherParser.LookupScopeOpenerContext, ctx: Document): StitcherToken {
        val marker = rule.scopeLookup.toLeafToken(ctx)
        val plus = rule.plus?.toLeafToken(ctx)
        val lookup = rule.literal?.acceptAs<LeafToken, _>(this, ctx)
        return ScopeToken.Lookup(marker, plus, lookup)
    }

    override fun visitScopeNamed(rule: StitcherParser.ScopeNamedContext, ctx: Document): StitcherToken {
        val marker = rule.scopeAs.toLeafToken(ctx)
        val id = rule.identifier.toLeafToken(ctx)
        return ScopeToken.Named(marker, id)
    }

    override fun visitSwapPrimary(rule: StitcherParser.SwapPrimaryContext, ctx: Document): StitcherToken {
        val sugar = listOf(rule.sugarIf.toLeafToken(ctx))
        val expr = rule.conditionExpression.acceptAs<ExpressionToken, _>(this, ctx)
        val literal = rule.literal.acceptAs<LeafToken, _>(this, ctx)
        return DefinitionToken.SwapLocal.Entry(sugar, expr, literal)
    }

    override fun visitSwapExtension(rule: StitcherParser.SwapExtensionContext, ctx: Document): StitcherToken {
        val sugar = collectElseSugar(rule.elseSugar, ctx)
        val expr = rule.conditionExpression.acceptAs<ExpressionToken, _>(this, ctx)
        val literal = rule.literal.acceptAs<LeafToken, _>(this, ctx)
        return DefinitionToken.SwapLocal.Entry(sugar, expr, literal)
    }

    override fun visitSwapFallback(rule: StitcherParser.SwapFallbackContext, ctx: Document): StitcherToken {
        val sugar = listOf(rule.sugarElse.toLeafToken(ctx))
        val literal = rule.literal.acceptAs<LeafToken, _>(this, ctx)
        return DefinitionToken.SwapLocal.Entry(sugar, null, literal)
    }

    override fun visitReplacementToggle(rule: StitcherParser.ReplacementToggleContext, ctx: Document): StitcherToken {
        val op = rule.opNot?.toLeafToken(ctx)
        val id = rule.identifier.toLeafToken(ctx)
        val comma = rule.comma?.toLeafToken(ctx)
        return DefinitionToken.ReplacementToggle.Entry(op, id, comma)
    }

    override fun visitReplacementEntry(rule: StitcherParser.ReplacementEntryContext, ctx: Document): StitcherToken {
        val (from, to) = rule.literal.map { it.acceptAs<LeafToken, _>(this, ctx) }
        val dir = rule.opDir.toLeafToken(ctx)
        val comma = rule.comma?.toLeafToken(ctx)
        return DefinitionToken.ReplacementLocal.Entry(from, dir, to, comma)
    }

    override fun visitLiteral(rule: StitcherParser.LiteralContext, ctx: Document): StitcherToken =
        rule.start.toLeafToken(ctx)

    private fun collectElseSugar(rule: StitcherParser.ElseSugarContext?, ctx: Document): List<LeafToken> =
        if (rule?.children.isNullOrEmpty()) emptyList()
        else listOfNotNull(rule.sugarElif, rule.sugarElse, rule.sugarIf).map { it.toLeafToken(ctx) }

    private fun buildSemanticVersion(rule: StitcherParser.SemanticVersionContext): SemanticVersion {
        val coreComponents = rule.versionCore.numeric.map { it.text.toInt() }
        val preRelease = rule.preRelease?.text.orEmpty()
        val buildMetadata = rule.buildMetadata?.text.orEmpty()
        return SemanticVersion(coreComponents, preRelease, buildMetadata)
    }

    private fun buildStringVersion(rule: StitcherParser.StringVersionContext): StringVersion =
        StringVersion(rule.text)

    private fun matchPredicateType(token: LeafToken?): VersionOperator = when(val it = token?.type ?: -1) {
        AntlrToken.EOF -> VersionOperator.IMPLICIT_EQUAL
        Tokens.COMP_EQUAL -> VersionOperator.EQUAL
        Tokens.COMP_NOT_EQUAL -> VersionOperator.NOT_EQUAL
        Tokens.COMP_MORE -> VersionOperator.GREATER
        Tokens.COMP_GR_MORE -> VersionOperator.GREATER_EQUAL
        Tokens.COMP_LESS -> VersionOperator.LESS
        Tokens.COMP_GR_LESS -> VersionOperator.LESS_EQUAL
        Tokens.REPL_MARK -> VersionOperator.SAME_MINOR
        Tokens.COMP_MAJOR -> VersionOperator.SAME_MAJOR
        else -> error("Invalid token type $it")
    }
}
