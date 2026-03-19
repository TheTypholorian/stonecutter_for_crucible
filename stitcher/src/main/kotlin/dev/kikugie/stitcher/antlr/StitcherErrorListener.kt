package dev.kikugie.stitcher.antlr

import dev.kikugie.commons.collections.present
import dev.kikugie.stitcher.antlr.StitcherLexer.*
import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.location
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.parser.SYNTAX_GROUP
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.IntervalSet

internal class StitcherErrorListener(val problems: FileProblemReporter) : BaseErrorListener() {
    private val reported: MutableSet<ErrorID> = mutableSetOf()

    override fun syntaxError(recognizer: Recognizer<*, *>, symbol: Any?, line: Int, charPositionInLine: Int, msg: String?, e: RecognitionException?) {
        val index = if (symbol is AntlrToken) symbol.startIndex else problems.index.reverse(line - 1, charPositionInLine)
        var (primary, secondary) = unmangle(msg).let { (a, b) -> (a ?: e?.message ?: "Unknown error") to b }
        if (!reported.add(ErrorID(primary, index)))
            return

        problems.report(SYNTAX_GROUP.child("antlr-error"), problems.location(line - 1, charPositionInLine, index)) {
            label = primary
            if (secondary != null) details = secondary
        }
    }

    private fun unmangle(message: String?): Pair<String?, String?> {
        val split = message?.split('\n', limit = 2) ?: return null to null
        return split.firstOrNull() to split.lastOrNull()
    }

    private data class ErrorID(val name: String, val position: Int)
}

// TODO: Add hints and doc links
internal object StitcherErrorStrategy : DefaultErrorStrategy() {
    private val EOF_INTR = TokenInterpreter { it.consume("<comment end>", AntlrToken.EOF) }
    private val ID_INTR = TokenInterpreter { it.consume("identifier", IDENTIFIER) }
    private val NUM_INTR = TokenInterpreter { it.consume("number", NUMERIC) }
    private val COMP_INTR = TokenInterpreter {
        it.consume("version comparator", REPL_MARK, COMP_MAJOR, COMP_EQUAL, COMP_NEQUAL, COMP_MORE, COMP_GMORE, COMP_LESS, COMP_GLESS)
    }

    private val INTERPRETERS = listOf(EOF_INTR, ID_INTR, NUM_INTR, COMP_INTR)

    override fun reportInputMismatch(recognizer: Parser, err: InputMismatchException) {
        val options = buildOptions(recognizer, err.expectedTokens)
        val message = "Mismatched input ${getTokenErrorDisplay(err.offendingToken)}\nExpected tokens: ${options.present()}"
        recognizer.notifyErrorListeners(err.offendingToken, message, err)
    }

    override fun reportUnwantedToken(recognizer: Parser) {
        if (inErrorRecoveryMode(recognizer)) return
        beginErrorCondition(recognizer)

        val options = buildOptions(recognizer, getExpectedTokens(recognizer))
        val message = "Extraneous input ${getTokenErrorDisplay(recognizer.currentToken)}\nExpected tokens: ${options.present()}"
        recognizer.notifyErrorListeners(recognizer.currentToken, message, null)
    }

    override fun reportMissingToken(recognizer: Parser) {
        if (inErrorRecoveryMode(recognizer)) return
        beginErrorCondition(recognizer)

        val options = buildOptions(recognizer, getExpectedTokens(recognizer))
        val message = "Missing token\nExpected tokens: ${options.present()}"
        recognizer.notifyErrorListeners(recognizer.currentToken, message, null)
    }

    override fun reportNoViableAlternative(recognizer: Parser, err: NoViableAltException) {
        val message = "No viable alternative\nThe parser is unable to find a valid matching path"
        recognizer.notifyErrorListeners(err.offendingToken, message, err)
    }

    override fun reportFailedPredicate(recognizer: Parser, err: FailedPredicateException) {
        val rule = recognizer.ruleNames[recognizer.ruleContext.ruleIndex]
        val message = "Unsatisfied parser predicate\nOccurred at rule $rule for ${err.predicate}"
        recognizer.notifyErrorListeners(err.offendingToken, message, err)
    }

    fun reportUnwantedWhitespace(recognizer: Parser, token: AntlrToken) {
        val message = "Invalid whitespace\nWhitespaces are not allowed around ${getTokenErrorDisplay(recognizer.currentToken)}"
        recognizer.notifyErrorListeners(token, message, null)
    }

    private fun buildOptions(parser: Parser, intervals: IntervalSet): Set<String> = buildSet {
        val set: MutableSet<Int> = intervals.toSet()
        for (intr in INTERPRETERS) intr.consume(set)?.let(this::add)
        for (rem in set) this += parser.vocabulary.getDisplayName(rem)
    }

    private fun interface TokenInterpreter {
        fun consume(variants: MutableSet<Int>): String?
    }

    private fun MutableSet<Int>.consume(description: String, type: Int): String? = description.takeIf { remove(type) }
    private fun MutableSet<Int>.consume(description: String, vararg types: Int): String? = description.takeIf { removeAll(types.asIterable()) }
}
