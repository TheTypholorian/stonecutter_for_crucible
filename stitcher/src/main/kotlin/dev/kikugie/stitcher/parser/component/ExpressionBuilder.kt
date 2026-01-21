package dev.kikugie.stitcher.parser.component

import dev.kikugie.stitcher.antlr.StitcherBaseVisitor
import dev.kikugie.stitcher.antlr.StitcherParser
import dev.kikugie.stitcher.issue.ProblemID
import dev.kikugie.stitcher.issue.ProblemReporter
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.issue.tryBailing
import dev.kikugie.stitcher.parser.StitcherTokenFactory
import dev.kikugie.stitcher.parser.fromAntlrNode
import dev.kikugie.stitcher.parser.fromAntlrToken
import dev.kikugie.stitcher.token.StitcherType
import dev.kikugie.stitcher.token.impl.ExpressionToken
import dev.kikugie.stitcher.token.impl.PredicateToken
import dev.kikugie.stitcher.util.length
import dev.kikugie.stitcher.util.startIndex

private val PREDICATE_PARSE: ProblemID = ProblemID.SYNTAX_GROUP.create("predicate-parse-exception")

internal class ExpressionBuilder(val factory: StitcherTokenFactory, val problems: ProblemReporter)
    : StitcherBaseVisitor<ExpressionToken>() {
    private val predicateBuilder: PredicateBuilder by lazy { PredicateBuilder(factory, problems) }

    override fun visitBinaryExpression(ctx: StitcherParser.BinaryExpressionContext): ExpressionToken.Binary {
        val operator = factory.fromAntlrToken(ctx.op, StitcherType.Leaf)
        val left = ctx.conditionExpression(0).accept(this)
        val right = ctx.conditionExpression(1).accept(this)
        return ExpressionToken.Binary(left, operator, right)
    }

    override fun visitUnaryExpression(ctx: StitcherParser.UnaryExpressionContext): ExpressionToken.Unary {
        val operator = factory.fromAntlrNode(ctx.OP_NOT(), StitcherType.Leaf)
        val target = ctx.conditionExpression().accept(this)
        return ExpressionToken.Unary(operator, target)
    }

    override fun visitGroupExpression(ctx: StitcherParser.GroupExpressionContext): ExpressionToken.Group {
        val lb = factory.fromAntlrNode(ctx.LEFT_BRACE(), StitcherType.Leaf)
        val rb = factory.fromAntlrNode(ctx.RIGHT_BRACE(), StitcherType.Leaf)
        val body = ctx.conditionExpression().accept(this)
        return ExpressionToken.Group(lb, body, rb)
    }

    override fun visitAssignmentExpression(ctx: StitcherParser.AssignmentExpressionContext): ExpressionToken.Assignment {
        val identifier = ctx.IDENTIFIER()?.let { factory.fromAntlrNode(it, StitcherType.Leaf) }
        val operator = ctx.OP_ASSIGN()?.let { factory.fromAntlrNode(it, StitcherType.Leaf) }
        val predicates = ctx.versionPredicate().mapNotNull(::parsePredicate)
        return if (identifier == null || operator == null) ExpressionToken.Assignment(null, null, predicates)
        else ExpressionToken.Assignment(identifier, operator, predicates)
    }

    override fun visitConstantExpression(ctx: StitcherParser.ConstantExpressionContext): ExpressionToken.Constant {
        val identifier = factory.fromAntlrNode(ctx.IDENTIFIER(), StitcherType.Leaf)
        return ExpressionToken.Constant(identifier)
    }

    private fun parsePredicate(ctx: StitcherParser.VersionPredicateContext): PredicateToken? = tryBailing {
        ctx.accept(predicateBuilder)
    }.onFailure {
        problems.report(PREDICATE_PARSE, ctx.startIndex, ctx.length) {
            label = "Unexpected error when parsing predicate"
            exception = it
        }
    }.getOrNull()
}