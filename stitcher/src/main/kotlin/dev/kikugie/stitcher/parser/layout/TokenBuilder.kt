package dev.kikugie.stitcher.parser.layout

import dev.kikugie.commons.takeAsOrNull
import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.semver.data.StringVersion
import dev.kikugie.semver.data.VersionOperator
import dev.kikugie.semver.data.VersionPredicate
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.StitcherBaseVisitor
import dev.kikugie.stitcher.antlr.StitcherLexer
import dev.kikugie.stitcher.antlr.StitcherParser
import dev.kikugie.stitcher.antlr.acceptAs
import dev.kikugie.stitcher.antlr.startIndex
import dev.kikugie.stitcher.issue.BailException
import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.bail
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.parser.SYNTAX_GROUP
import dev.kikugie.stitcher.parser.StitcherTokenFactory
import dev.kikugie.stitcher.parser.fromAntlrNode
import dev.kikugie.stitcher.parser.fromAntlrRule
import dev.kikugie.stitcher.parser.fromAntlrToken
import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.ExpressionToken
import dev.kikugie.stitcher.token.impl.LeafToken
import dev.kikugie.stitcher.token.impl.PredicateToken
import dev.kikugie.stitcher.token.impl.ScopeToken
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode

internal class MarkedTokenBuilder(factory: StitcherTokenFactory, problems: FileProblemReporter): StitcherBaseVisitor<MarkedTokenBuilder.Result?>() {
    private val delegate: TokenBuilder = TokenBuilder(factory, problems)

    override fun visitConditionDefinition(ctx: StitcherParser.ConditionDefinitionContext): Result? {
        return Result(ctx.COND_MARK().symbol, ctx.condition()?.accept(delegate) as? DefinitionToken ?: return null)
    }

    override fun visitSwapDefinition(ctx: StitcherParser.SwapDefinitionContext): Result? {
        return Result(ctx.SWAP_MARK().symbol, ctx.swap()?.accept(delegate) as? DefinitionToken ?: return null)
    }

    override fun visitReplacementDefinition(ctx: StitcherParser.ReplacementDefinitionContext): Result? {
        return Result(ctx.REPL_MARK().symbol, ctx.replacement()?.accept(delegate) as? DefinitionToken ?: return null)
    }

    data class Result(val marker: AntlrToken, val definition: DefinitionToken)
}

internal class TokenBuilder(
    private val factory: StitcherTokenFactory,
    private val problems: FileProblemReporter
) : StitcherBaseVisitor<StitcherToken>() {
    override fun visitOpenerCondition(ctx: StitcherParser.OpenerConditionContext): DefinitionToken.ConditionOpener {
        val sugar = listOfNotNull(ctx.SUGAR_IF()).map(factory::fromAntlrNode)
        val expression = ctx.conditionExpression().acceptAs<ExpressionToken>(this)
        val opener = ctx.scopeOpener()?.acceptAs<ScopeToken>(this)
        return DefinitionToken.ConditionOpener(sugar, expression, opener)
    }

    override fun visitExtensionCondition(ctx: StitcherParser.ExtensionConditionContext): DefinitionToken.ConditionExtension {
        val closer = factory.fromAntlrNode(ctx.SCOPE_CLOSE())
        val sugar = ctx.elseSugar().collectToLeaves(factory)
        val expression = ctx.conditionExpression().acceptAs<ExpressionToken>(this)
        val opener = ctx.scopeOpener()?.acceptAs<ScopeToken>(this)
        return DefinitionToken.ConditionExtension(closer, sugar, expression, opener)
    }

    override fun visitElseExtCondition(ctx: StitcherParser.ElseExtConditionContext): DefinitionToken.ConditionExtension {
        val closer = factory.fromAntlrNode(ctx.SCOPE_CLOSE())
        val sugar = listOf(factory.fromAntlrNode(ctx.SUGAR_ELSE()))
        val opener = ctx.scopeOpener()?.acceptAs<ScopeToken>(this)
        return DefinitionToken.ConditionExtension(closer, sugar, null, opener)
    }

    override fun visitCloserCondition(ctx: StitcherParser.CloserConditionContext): DefinitionToken.ConditionCloser {
        val closer = factory.fromAntlrNode(ctx.SCOPE_CLOSE())
        return DefinitionToken.ConditionCloser(closer)
    }

    override fun visitIdentifierSwap(ctx: StitcherParser.IdentifierSwapContext): DefinitionToken.SwapOpener {
        val identifier = factory.fromAntlrNode(ctx.IDENTIFIER())
        val arguments = ctx.swapArguments()?.literal().orEmpty<StitcherParser.LiteralContext>()
            .map { factory.fromAntlrToken(it.start) }
        val opener = ctx.scopeOpener()?.acceptAs<ScopeToken>(this)
        return DefinitionToken.SwapOpener(identifier, arguments, opener)
    }

    override fun visitLocalSwap(ctx: StitcherParser.LocalSwapContext): DefinitionToken.SwapLocal {
        val expressions = buildList<DefinitionToken.SwapLocal.Entry> {
            add(ctx.swapPrimary().acceptAs(this@TokenBuilder))
            for (ext in ctx.swapExtension())
                add(ext.acceptAs(this@TokenBuilder))
            add(ctx.swapFallback().acceptAs(this@TokenBuilder))
        }
        val opener = ctx.scopeOpener()?.acceptAs<ScopeToken>(this)
        return DefinitionToken.SwapLocal(expressions, opener)
    }

    override fun visitCloserSwap(ctx: StitcherParser.CloserSwapContext): DefinitionToken.SwapCloser =
        DefinitionToken.SwapCloser(factory.fromAntlrNode(ctx.SCOPE_CLOSE()))

    override fun visitToggleReplacement(ctx: StitcherParser.ToggleReplacementContext): DefinitionToken.ReplacementToggle {
        val entries = ctx.replacementToggle().map { it.acceptAs<DefinitionToken.ReplacementToggle.Entry>(this) }
        return DefinitionToken.ReplacementToggle(entries)
    }

    override fun visitLocalReplacement(ctx: StitcherParser.LocalReplacementContext): DefinitionToken.ReplacementLocal {
        val sugar = factory.fromAntlrNode(ctx.SUGAR_IF())
        val expression = ctx.conditionExpression().acceptAs<ExpressionToken>(this)
        val entries = ctx.replacementEntry().map { it.acceptAs<DefinitionToken.ReplacementLocal.Entry>(this) }
        val opener = (ctx.scopeOpener() ?: ctx.scopeNamed())?.acceptAs<ScopeToken>(this)
        return DefinitionToken.ReplacementLocal(sugar, expression, entries, opener)
    }

    override fun visitCloserReplacement(ctx: StitcherParser.CloserReplacementContext): DefinitionToken.ReplacementCloser =
        DefinitionToken.ReplacementCloser(factory.fromAntlrNode(ctx.SCOPE_CLOSE()))

    override fun visitBinaryExpression(ctx: StitcherParser.BinaryExpressionContext): ExpressionToken.Binary {
        val operator = factory.fromAntlrToken(ctx.op)
        val left = ctx.conditionExpression(0).acceptAs<ExpressionToken>(this)
        val right = ctx.conditionExpression(1).acceptAs<ExpressionToken>(this)
        return ExpressionToken.Binary(left, operator, right)
    }

    override fun visitUnaryExpression(ctx: StitcherParser.UnaryExpressionContext): ExpressionToken.Unary {
        val operator = factory.fromAntlrNode(ctx.OP_NOT())
        val target = ctx.conditionExpression().acceptAs<ExpressionToken>(this)
        return ExpressionToken.Unary(operator, target)
    }

    override fun visitGroupExpression(ctx: StitcherParser.GroupExpressionContext): ExpressionToken.Group {
        val lb = factory.fromAntlrNode(ctx.LEFT_BRACE())
        val rb = factory.fromAntlrNode(ctx.RIGHT_BRACE())
        val body = ctx.conditionExpression().acceptAs<ExpressionToken>(this)
        return ExpressionToken.Group(lb, body, rb)
    }

    override fun visitAssignmentExpression(ctx: StitcherParser.AssignmentExpressionContext): ExpressionToken.Assignment {
        val identifier = ctx.IDENTIFIER()?.let(factory::fromAntlrNode)
        val operator = ctx.OP_ASSIGN()?.let(factory::fromAntlrNode)
        val predicates = ctx.versionPredicate().mapNotNull { it.tryToPredicate(this, problems) }
        return ExpressionToken.Assignment(identifier, operator, predicates)
    }

    override fun visitConstantExpression(ctx: StitcherParser.ConstantExpressionContext): ExpressionToken.Constant {
        val identifier = factory.fromAntlrNode(ctx.IDENTIFIER())
        return ExpressionToken.Constant(identifier)
    }

    override fun visitSemanticPredicate(ctx: StitcherParser.SemanticPredicateContext): PredicateToken {
        val comparator = (ctx.semanticComparator() ?: ctx.stringComparator())
        val version = ctx.semanticVersion()
        return PredicateToken(
            comparator?.acceptAs(this), version.acceptAs(this),
            VersionPredicate(comparator.toVersionOperator(problems), version.toSemanticVersion())
        )
    }

    override fun visitStringPredicate(ctx: StitcherParser.StringPredicateContext): PredicateToken {
        val comparator = ctx.stringComparator()
        val version = ctx.stringVersion()
        return PredicateToken(
            comparator?.acceptAs(this), version.acceptAs(this),
            VersionPredicate(comparator.toVersionOperator(problems), version.toStringVersion())
        )
    }

    override fun visitSemanticComparator(ctx: StitcherParser.SemanticComparatorContext): LeafToken =
        factory.fromAntlrRule(ctx, NativeStitcherType.OPERATOR)

    override fun visitStringComparator(ctx: StitcherParser.StringComparatorContext): LeafToken =
        factory.fromAntlrRule(ctx, NativeStitcherType.OPERATOR)

    override fun visitSemanticVersion(ctx: StitcherParser.SemanticVersionContext): LeafToken =
        factory.fromAntlrRule(ctx, NativeStitcherType.VERSION)

    override fun visitStringVersion(ctx: StitcherParser.StringVersionContext): LeafToken =
        factory.fromAntlrRule(ctx, NativeStitcherType.VERSION)

    override fun visitClosedScopeOpener(ctx: StitcherParser.ClosedScopeOpenerContext): ScopeToken.Closed =
        ScopeToken.Closed(factory.fromAntlrNode(ctx.SCOPE_OPEN()))

    override fun visitLookupScopeOpener(ctx: StitcherParser.LookupScopeOpenerContext): ScopeToken.Lookup {
        val marker = factory.fromAntlrNode(ctx.SCOPE_LOOKUP())
        val plus = ctx.PLUS()?.let(factory::fromAntlrNode)
        val lookup = ctx.literal()?.let { factory.fromAntlrToken(it.start) }
        return ScopeToken.Lookup(marker, plus, lookup)
    }

    override fun visitScopeNamed(ctx: StitcherParser.ScopeNamedContext): ScopeToken.Named {
        val marker = factory.fromAntlrNode(ctx.SCOPE_AS())
        val identifier = factory.fromAntlrNode(ctx.IDENTIFIER())
        return ScopeToken.Named(marker, identifier)
    }

    override fun visitSwapPrimary(ctx: StitcherParser.SwapPrimaryContext): DefinitionToken.SwapLocal.Entry {
        val sugar = listOf(factory.fromAntlrNode(ctx.SUGAR_IF()))
        val expression = ctx.conditionExpression().acceptAs<ExpressionToken>(this)
        val literal = factory.fromAntlrToken(ctx.literal().start)
        return DefinitionToken.SwapLocal.Entry(sugar, expression, literal)
    }

    override fun visitSwapExtension(ctx: StitcherParser.SwapExtensionContext): DefinitionToken.SwapLocal.Entry {
        val sugar = ctx.elseSugar().collectToLeaves(factory)
        val expression = ctx.conditionExpression().acceptAs<ExpressionToken>(this)
        val literal = factory.fromAntlrToken(ctx.literal().start)
        return DefinitionToken.SwapLocal.Entry(sugar, expression, literal)
    }

    override fun visitSwapFallback(ctx: StitcherParser.SwapFallbackContext): DefinitionToken.SwapLocal.Entry {
        val sugar = listOf(factory.fromAntlrNode(ctx.SUGAR_ELSE()))
        val literal = factory.fromAntlrToken(ctx.literal().start)
        return DefinitionToken.SwapLocal.Entry(sugar, null, literal)
    }

    override fun visitReplacementToggle(ctx: StitcherParser.ReplacementToggleContext): DefinitionToken.ReplacementToggle.Entry {
        val op = ctx.OP_NOT()?.let(factory::fromAntlrNode)
        val identifier = factory.fromAntlrNode(ctx.IDENTIFIER())
        val comma = ctx.COMMA()?.let(factory::fromAntlrNode)
        return DefinitionToken.ReplacementToggle.Entry(op, identifier, comma)
    }

    override fun visitReplacementEntry(ctx: StitcherParser.ReplacementEntryContext): DefinitionToken.ReplacementLocal.Entry {
        val dir = factory.fromAntlrNode(ctx.OP_DIR())
        val from = factory.fromAntlrToken(ctx.literal(0).start)
        val to = factory.fromAntlrToken(ctx.literal(1).start)
        val comma = ctx.COMMA()?.let(factory::fromAntlrNode)
        return DefinitionToken.ReplacementLocal.Entry(from, dir, to, comma)
    }
}

private val ParseTree?.type: Int
    get() = this?.takeAsOrNull<TerminalNode>()?.symbol?.type ?: -1

private fun StitcherParser.ElseSugarContext?.collectToLeaves(factory: StitcherTokenFactory): List<LeafToken> =
    if (this == null) emptyList() else listOfNotNull(SUGAR_ELIF(), SUGAR_ELSE(), SUGAR_IF()).map(factory::fromAntlrNode)

private fun StitcherParser.VersionPredicateContext.tryToPredicate(visitor: TokenBuilder, problems: FileProblemReporter): PredicateToken? = try {
    acceptAs(visitor)
} catch (e: Exception) {
    if (e !is BailException) problems.report(SYNTAX_GROUP.child("predicate-parse-exception"), startIndex) {
        label = "Unexpected error when parsing predicate"
        exception = e
    }

    null
}

private fun ParserRuleContext?.toVersionOperator(problems: FileProblemReporter): VersionOperator = when(this?.getChild(0).type) {
    AntlrToken.EOF -> VersionOperator.IMPLICIT_EQUAL
    StitcherLexer.COMP_EQUAL -> VersionOperator.EQUAL
    StitcherLexer.COMP_NEQUAL -> VersionOperator.NOT_EQUAL
    StitcherLexer.COMP_MORE -> VersionOperator.GREATER
    StitcherLexer.COMP_GMORE -> VersionOperator.GREATER_EQUAL
    StitcherLexer.COMP_LESS -> VersionOperator.LESS
    StitcherLexer.COMP_GLESS -> VersionOperator.LESS_EQUAL
    StitcherLexer.REPL_MARK -> VersionOperator.SAME_MINOR
    StitcherLexer.COMP_MAJOR -> VersionOperator.SAME_MAJOR
    else -> problems.bail(SYNTAX_GROUP.child("invalid-comparison-operator"), this!!.startIndex) {
        label = "Invalid version predicate. How did we get here?"
    }
}

private fun StitcherParser.SemanticVersionContext.toSemanticVersion(): SemanticVersion {
    val components = versionCore().NUMERIC().map<TerminalNode, Int> { it.text.toInt() }
    val preRelease = preRelease()?.text.orEmpty()
    val buildMetadata = buildMetadata()?.text.orEmpty()
    return SemanticVersion(components, preRelease, buildMetadata)
}

private fun StitcherParser.StringVersionContext.toStringVersion(): StringVersion =
    StringVersion(text)
