@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.issue.parser

import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.StitcherLexer.*
import dev.kikugie.stitcher.antlr.StitcherParser
import dev.kikugie.stitcher.issue.ProblemID
import dev.kikugie.stitcher.parser.SYNTAX_GROUP
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.RecognitionException

fun interface ParserErrorInterpreter {
    fun handle(parser: Parser, error: RecognitionException): ProblemID?
}

object DefaultErrorInterpreters {
    private val COMPARATORS = setOf(REPL_MARK, COMP_MAJOR, COMP_EQUAL, COMP_NEQUAL, COMP_MORE, COMP_GMORE, COMP_LESS, COMP_GLESS)
    private val OPENERS = setOf(SCOPE_OPEN, SCOPE_WORD, AntlrToken.EOF)
    private val EXTENSIONS = setOf(SUGAR_ELSE, SUGAR_ELIF)

    internal const val EMPTY_CONDITION_ID = "empty-cond-expr"
    internal const val MISSING_CLOSER_ID = "no-scope-closer"

    val EMPTY_CONDITION = ParserErrorInterpreter { _, error ->
        if (error.ctx !is StitcherParser.ConditionExpressionContext
            || error.offendingToken.isNotOf(OPENERS))
            return@ParserErrorInterpreter null
        SYNTAX_GROUP.child(EMPTY_CONDITION_ID)
    }

    val MISSING_CLOSER = ParserErrorInterpreter { _, error ->
        if (error.ctx !is StitcherParser.ConditionContext
            || error.offendingToken.isNotOf(EXTENSIONS))
            return@ParserErrorInterpreter null
        SYNTAX_GROUP.child(MISSING_CLOSER_ID)
    }
}

private inline fun AntlrToken.isNotOf(types: Collection<Int>): Boolean = !isOf(types)
private inline fun AntlrToken.isOf(types: Collection<Int>): Boolean = type in types
