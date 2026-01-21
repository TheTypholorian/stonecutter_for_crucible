package dev.kikugie.stitcher.parser.component

import dev.kikugie.commons.takeAsOrNull
import dev.kikugie.semver.data.*
import dev.kikugie.stitcher.antlr.StitcherBaseVisitor
import dev.kikugie.stitcher.antlr.StitcherLexer
import dev.kikugie.stitcher.antlr.StitcherParser
import dev.kikugie.stitcher.issue.ProblemID
import dev.kikugie.stitcher.issue.ProblemReporter
import dev.kikugie.stitcher.issue.bail
import dev.kikugie.stitcher.parser.StitcherTokenFactory
import dev.kikugie.stitcher.parser.fromAntlrRule
import dev.kikugie.stitcher.token.StitcherType
import dev.kikugie.stitcher.token.impl.PredicateToken
import dev.kikugie.stitcher.util.length
import dev.kikugie.stitcher.util.startIndex
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode

private val INVALID_COMPARATOR: ProblemID = ProblemID.SYNTAX_GROUP.create("invalid-comparison-operator")

private val ParseTree?.type: Int
    get() = this?.takeAsOrNull<TerminalNode>()?.symbol?.type ?: -1

internal class PredicateBuilder(val factory: StitcherTokenFactory, val problems: ProblemReporter)
    : StitcherBaseVisitor<PredicateToken>(){
    override fun visitSemanticPredicate(ctx: StitcherParser.SemanticPredicateContext): PredicateToken {
        val comparator = ctx.semanticComparator()?.resolve()
            ?: ctx.stringComparator()?.resolve()
            ?: VersionOperator.IMPLICIT_EQUAL
        val version = ctx.semanticVersion().build()
        return PredicateToken(factory.fromAntlrRule(ctx, StitcherType.Custom.PREDICATE), VersionPredicate(comparator, version))
    }

    override fun visitStringPredicate(ctx: StitcherParser.StringPredicateContext): PredicateToken {
        val comparator = ctx.stringComparator().resolve()
        val version = ctx.stringVersion().build()
        return PredicateToken(factory.fromAntlrRule(ctx, StitcherType.Custom.PREDICATE), VersionPredicate(comparator, version))
    }

    private fun ParserRuleContext.resolve(): VersionOperator = when(getChild(0).type) {
        StitcherLexer.COMP_EQUAL -> VersionOperator.EQUAL
        StitcherLexer.COMP_NEQUAL -> VersionOperator.NOT_EQUAL
        StitcherLexer.COMP_MORE -> VersionOperator.GREATER
        StitcherLexer.COMP_GMORE -> VersionOperator.GREATER_EQUAL
        StitcherLexer.COMP_LESS -> VersionOperator.LESS
        StitcherLexer.COMP_GLESS -> VersionOperator.LESS_EQUAL
        StitcherLexer.REPL_MARK -> VersionOperator.SAME_MINOR
        StitcherLexer.COMP_MAJOR -> VersionOperator.SAME_MAJOR
        else -> problems.bail(INVALID_COMPARATOR, startIndex, length) { label = "Invalid version predicate. How did we get here?" }
    }

    private fun StitcherParser.StringVersionContext.build(): Version =
        StringVersion(text)

    private fun StitcherParser.SemanticVersionContext.build(): Version {
        val components = versionCore().NUMERIC().map<TerminalNode, Int> { it.text.toInt() }.toIntArray()
        val preRelease = preRelease()?.text.orEmpty()
        val buildMetadata = buildMetadata()?.text.orEmpty()
        return SemanticVersion(components, preRelease, buildMetadata)
    }
}