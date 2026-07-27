package dev.kikugie.stitcher.antlr

import dev.kikugie.commons.collections.present
import dev.kikugie.stitcher.antlr.StitcherLexer.Tokens
import org.antlr.v4.runtime.DefaultErrorStrategy
import org.antlr.v4.runtime.FailedPredicateException
import org.antlr.v4.runtime.InputMismatchException
import org.antlr.v4.runtime.NoViableAltException
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.misc.IntervalSet

object StitcherErrorStrategy : DefaultErrorStrategy() {
    private val EOF_INTERPRETER: TokenInterpreter = TokenInterpreter { it.consume("<comment end>", AntlrToken.EOF) }
    private val IDENTIFIER_INTERPRETER: TokenInterpreter = TokenInterpreter { it.consume("identifier", Tokens.IDENTIFIER) }
    private val VERSION_INTERPRETER: TokenInterpreter = TokenInterpreter { it.consume("version", Tokens.VERSION) }
    private val COMPARATOR_INTERPRETER: TokenInterpreter = TokenInterpreter {
        it.consume(
            "version comparator",
            Tokens.REPL_MARK, Tokens.COMP_MAJOR, Tokens.COMP_EQUAL, Tokens.COMP_NOT_EQUAL,
            Tokens.COMP_MORE, Tokens.COMP_GR_MORE, Tokens.COMP_LESS, Tokens.COMP_GR_LESS,
        )
    }

    private val ALL_INTERPRETERS: List<TokenInterpreter> = listOf(EOF_INTERPRETER, IDENTIFIER_INTERPRETER, VERSION_INTERPRETER, COMPARATOR_INTERPRETER)

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

    private fun buildOptions(parser: Parser, intervals: IntervalSet): Set<String> = buildSet {
        val set: MutableSet<Int> = intervals.toSet()
        for (intr in ALL_INTERPRETERS) intr.consume(set)?.let(this::add)
        for (rem in set) this += parser.vocabulary.getDisplayName(rem)
    }

    private fun MutableSet<Int>.consume(description: String, type: Int): String? =
        description.takeIf { remove(type) }

    private fun MutableSet<Int>.consume(description: String, vararg types: Int): String? =
        description.takeIf { removeAll(types.asIterable()) }

    private fun interface TokenInterpreter {
        fun consume(variants: MutableSet<Int>): String?
    }
}