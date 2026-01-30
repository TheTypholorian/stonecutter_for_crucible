package dev.kikugie.stitcher.parser.component

import dev.kikugie.stitcher.antlr.*
import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.bail
import dev.kikugie.stitcher.issue.runBailing
import dev.kikugie.stitcher.parser.SYNTAX_GROUP
import dev.kikugie.stitcher.parser.StitcherTokenFactory
import dev.kikugie.stitcher.parser.fromAntlrNode
import dev.kikugie.stitcher.parser.fromAntlrToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.ExpressionToken
import dev.kikugie.stitcher.token.impl.ScopeToken

internal class DefinitionBuilder(val factory: StitcherTokenFactory, val problems: FileProblemReporter)
    : StitcherBaseVisitor<DefinitionToken>() {
    private val expressionBuilder: ExpressionBuilder by lazy { ExpressionBuilder(factory, problems) }
    private val scopeTokenBuilder: ScopeTokenBuilder by lazy { ScopeTokenBuilder(factory) }

    override fun visitReplacement(ctx: StitcherParser.ReplacementContext): DefinitionToken {
        val tokens = ctx.replacementEntry()
            .map { it.accept(expressionBuilder) }
        return DefinitionToken.Replacement(tokens)
    }

    override fun visitOpenerSwap(ctx: StitcherParser.OpenerSwapContext): DefinitionToken {
        val identifier = factory.fromAntlrNode(ctx.IDENTIFIER())
        val arguments = ctx.swapArguments()?.literal().orEmpty<StitcherParser.LiteralContext>()
            .map { factory.fromAntlrToken(it.start) }
        val opener = ctx.scopeOpener()?.accept(scopeTokenBuilder)
        return DefinitionToken.SwapOpener(identifier, arguments, opener)
    }

    override fun visitCloserSwap(ctx: StitcherParser.CloserSwapContext): DefinitionToken {
        val closer = factory.fromAntlrNode(ctx.SCOPE_CLOSE())
        return DefinitionToken.SwapCloser(closer)
    }

    override fun visitOpenerCondition(ctx: StitcherParser.OpenerConditionContext): DefinitionToken {
        val sugar = listOfNotNull(ctx.SUGAR_IF()).map(factory::fromAntlrNode)
        val expression = ctx.conditionExpression().let(::parseExpression)
        val opener = ctx.scopeOpener()?.accept(scopeTokenBuilder)
        return DefinitionToken.ConditionOpener(sugar, expression, opener)
    }

    override fun visitExtensionCondition(ctx: StitcherParser.ExtensionConditionContext): DefinitionToken {
        val closer = factory.fromAntlrNode(ctx.SCOPE_CLOSE())
        val sugar = listOfNotNull(ctx.SUGAR_ELIF(), ctx.SUGAR_ELSE(), ctx.SUGAR_IF()).map(factory::fromAntlrNode)
        val expression = ctx.conditionExpression()?.let(::parseExpression)
        val opener = ctx.scopeOpener()?.accept(scopeTokenBuilder)
        return DefinitionToken.ConditionExtension(closer, sugar, expression, opener)
    }

    override fun visitCloserCondition(ctx: StitcherParser.CloserConditionContext): DefinitionToken {
        val closer = factory.fromAntlrNode(ctx.SCOPE_CLOSE())
        return DefinitionToken.ConditionCloser(closer)
    }

    private fun parseExpression(ctx: StitcherParser.ConditionExpressionContext): ExpressionToken = runBailing {
        ctx.accept(expressionBuilder)
    }.getOrElse {
        problems.bail(SYNTAX_GROUP.child("expression-parse-exception"), ctx.startIndex) {
            label = "Unexpected error when parsing expression"
            exception = it
        }
    }

    private class PairDefinitionBuilder(factory: StitcherTokenFactory, problems: FileProblemReporter) : StitcherBaseVisitor<Pair<AntlrToken, DefinitionToken>>() {
        private val definitionBuilder = DefinitionBuilder(factory, problems)

        override fun visitConditionDefinition(ctx: StitcherParser.ConditionDefinitionContext): Pair<AntlrToken, DefinitionToken> =
            ctx.COND_MARK().symbol to ctx.condition().accept(definitionBuilder)

        override fun visitSwapDefinition(ctx: StitcherParser.SwapDefinitionContext): Pair<AntlrToken, DefinitionToken> =
            ctx.SWAP_MARK().symbol to ctx.swap().accept(definitionBuilder)

        override fun visitReplacementDefinition(ctx: StitcherParser.ReplacementDefinitionContext): Pair<AntlrToken, DefinitionToken> =
            ctx.REPL_MARK().symbol to ctx.replacement().accept(definitionBuilder)
    }

    private class ScopeTokenBuilder(val factory: StitcherTokenFactory) : StitcherBaseVisitor<ScopeToken>() {
        override fun visitClosedScopeOpener(ctx: StitcherParser.ClosedScopeOpenerContext): ScopeToken {
            val marker = factory.fromAntlrNode(ctx.SCOPE_OPEN())
            return ScopeToken.Closed(marker)
        }

        override fun visitWordScopeOpener(ctx: StitcherParser.WordScopeOpenerContext): ScopeToken {
            val marker = factory.fromAntlrNode(ctx.SCOPE_WORD())
            val plus = ctx.PLUS()?.let(factory::fromAntlrNode)
            val lookup = ctx.PLUS()?.let(factory::fromAntlrNode)
            return ScopeToken.Lookup(marker, plus, lookup)
        }
    }

    companion object {
        fun paired(factory: StitcherTokenFactory, problems: FileProblemReporter): StitcherVisitor<Pair<AntlrToken, DefinitionToken>> =
            PairDefinitionBuilder(factory, problems)
    }
}