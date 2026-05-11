package dev.kikugie.stitcher.parser.component

import dev.kikugie.stitcher.antlr.*
import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.bail
import dev.kikugie.stitcher.issue.runBailing
import dev.kikugie.stitcher.parser.SYNTAX_GROUP
import dev.kikugie.stitcher.parser.StitcherTokenFactory
import dev.kikugie.stitcher.parser.fromAntlrNode
import dev.kikugie.stitcher.parser.fromAntlrRule
import dev.kikugie.stitcher.parser.fromAntlrToken
import dev.kikugie.stitcher.token.AntlrStitcherType
import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.ExpressionToken
import dev.kikugie.stitcher.token.impl.LeafToken
import dev.kikugie.stitcher.token.impl.ScopeToken

internal class DefinitionBuilder(val factory: StitcherTokenFactory, val problems: FileProblemReporter)
    : StitcherBaseVisitor<DefinitionToken>() {
    private val expressionBuilder: ExpressionBuilder = ExpressionBuilder(factory, problems)
    private val scopeTokenBuilder: ScopeTokenBuilder = ScopeTokenBuilder(factory)

    override fun visitOpenerCondition(ctx: StitcherParser.OpenerConditionContext): DefinitionToken {
        val sugar = listOfNotNull(ctx.SUGAR_IF()).map(factory::fromAntlrNode)
        val expression = parseExpression(ctx.conditionExpression())
        val opener = ctx.scopeOpener()?.accept(scopeTokenBuilder)
        return DefinitionToken.ConditionOpener(sugar, expression, opener)
    }

    override fun visitExtensionCondition(ctx: StitcherParser.ExtensionConditionContext): DefinitionToken {
        val closer = factory.fromAntlrNode(ctx.SCOPE_CLOSE())
        val expression = parseExpression(ctx.conditionExpression())
        val opener = ctx.scopeOpener()?.accept(scopeTokenBuilder)
        return DefinitionToken.ConditionExtension(closer, collectSugar(ctx.elseSugar()), expression, opener)
    }

    override fun visitElseExtCondition(ctx: StitcherParser.ElseExtConditionContext): DefinitionToken {
        val closer = factory.fromAntlrNode(ctx.SCOPE_CLOSE())
        val sugar = listOf(factory.fromAntlrNode(ctx.SUGAR_ELSE()))
        val opener = ctx.scopeOpener()?.accept(scopeTokenBuilder)
        return DefinitionToken.ConditionExtension(closer, sugar, null, opener)
    }

    override fun visitCloserCondition(ctx: StitcherParser.CloserConditionContext): DefinitionToken {
        val closer = factory.fromAntlrNode(ctx.SCOPE_CLOSE())
        return DefinitionToken.ConditionCloser(closer)
    }

    override fun visitIdentifierSwap(ctx: StitcherParser.IdentifierSwapContext): DefinitionToken {
        val identifier = factory.fromAntlrNode(ctx.IDENTIFIER())
        val arguments = ctx.swapArguments()?.literal().orEmpty<StitcherParser.LiteralContext>()
            .map { factory.fromAntlrToken(it.start) }
        val opener = ctx.scopeOpener()?.accept(scopeTokenBuilder)
        return DefinitionToken.SwapOpener(identifier, arguments, opener)
    }

    override fun visitLocalSwap(ctx: StitcherParser.LocalSwapContext): DefinitionToken {
        val expressions = mutableListOf<DefinitionToken.SwapExpr>()
        expressions += kotlin.run {
            val sugar = listOf(factory.fromAntlrNode(ctx.SUGAR_IF()))
            val expression = parseExpression(ctx.conditionExpression())
            val literal = factory.fromAntlrToken(ctx.literal(0).start)
            DefinitionToken.SwapExpr(sugar, expression, literal)
        }
        for (extension in ctx.swapExtension())
            expressions += parseSwapExtension(extension)
        expressions += kotlin.run {
            val sugar = listOf(factory.fromAntlrNode(ctx.SUGAR_ELSE()))
            val literal = factory.fromAntlrToken(ctx.literal(1).start)
            DefinitionToken.SwapExpr(sugar, null, literal)
        }
        val opener = ctx.scopeOpener()?.accept(scopeTokenBuilder)
        return DefinitionToken.SwapLocal(expressions, opener)
    }

    override fun visitCloserSwap(ctx: StitcherParser.CloserSwapContext): DefinitionToken {
        val closer = factory.fromAntlrNode(ctx.SCOPE_CLOSE())
        return DefinitionToken.SwapCloser(closer)
    }

    override fun visitToggleReplacement(ctx: StitcherParser.ToggleReplacementContext): DefinitionToken {
        val tokens = ctx.replacementEntry().map(::parseReplacementEntry)
        return DefinitionToken.ReplacementToggle(tokens)
    }

    override fun visitLocalReplacement(ctx: StitcherParser.LocalReplacementContext): DefinitionToken {
        val sugar = factory.fromAntlrNode(ctx.SUGAR_IF())
        val expression = parseExpression(ctx.conditionExpression())
        val (source, target) = ctx.literal().map { factory.fromAntlrToken(it.start) }
        val op = factory.fromAntlrNode(ctx.OP_DIR())
        val opener = ctx.scopeOpener()?.accept(scopeTokenBuilder)
        return DefinitionToken.ReplacementLocal(sugar, expression, source, op, target, opener)
    }

    override fun visitCloserReplacement(ctx: StitcherParser.CloserReplacementContext): DefinitionToken {
        val closer = factory.fromAntlrNode(ctx.SCOPE_CLOSE())
        return DefinitionToken.ReplacementCloser(closer)
    }

    private fun collectSugar(ctx: StitcherParser.ElseSugarContext?): List<LeafToken> =
        if (ctx == null) emptyList()
        else listOfNotNull(ctx.SUGAR_ELIF(), ctx.SUGAR_ELSE(), ctx.SUGAR_IF()).map(factory::fromAntlrNode)

    private fun parseExpression(ctx: StitcherParser.ConditionExpressionContext): ExpressionToken = runBailing {
        ctx.accept(expressionBuilder) ?: ExpressionToken.Constant(factory.fromAntlrRule(ctx, NativeStitcherType.CONSTANT))
    }.getOrElse {
        problems.bail(SYNTAX_GROUP.child("expression-parse-exception"), ctx.startIndex) {
            label = "Unexpected error when parsing expression"
            exception = it
        }
    }

    private fun parseSwapExtension(ctx: StitcherParser.SwapExtensionContext): DefinitionToken.SwapExpr {
        val sugar = collectSugar(ctx.elseSugar())
        val expression = ctx.conditionExpression()?.let(::parseExpression)
        val literal = factory.fromAntlrToken(ctx.literal().start)
        return DefinitionToken.SwapExpr(sugar, expression, literal)
    }

    private fun parseReplacementEntry(ctx: StitcherParser.ReplacementEntryContext): DefinitionToken.ReplacementEntry =
        DefinitionToken.ReplacementEntry(ctx.OP_NOT()?.let(factory::fromAntlrNode), factory.fromAntlrNode(ctx.IDENTIFIER()))

    private class PairDefinitionBuilder(factory: StitcherTokenFactory, problems: FileProblemReporter) : StitcherBaseVisitor<Pair<AntlrToken, DefinitionToken>>() {
        private val definitionBuilder = DefinitionBuilder(factory, problems)

        // TODO: Properly handle unmatched definitions
        override fun visitConditionDefinition(ctx: StitcherParser.ConditionDefinitionContext): Pair<AntlrToken, DefinitionToken> {
            val marker = ctx.COND_MARK().symbol
            val def = ctx.condition().accept(definitionBuilder) ?: kotlin.run {
                val type = AntlrStitcherType.of(StitcherLexer.SCOPE_CLOSE)
                val token = LeafToken(type, marker.startIndex, marker.stopIndex, "")
                DefinitionToken.ConditionExtension(token, emptyList(), null, null)
            }

            return marker to def
        }

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
            val lookup = ctx.literal()?.let { factory.fromAntlrToken(it.start) }
            return ScopeToken.Lookup(marker, plus, lookup)
        }
    }

    companion object {
        fun paired(factory: StitcherTokenFactory, problems: FileProblemReporter): StitcherVisitor<Pair<AntlrToken, DefinitionToken>> =
            PairDefinitionBuilder(factory, problems)
    }
}