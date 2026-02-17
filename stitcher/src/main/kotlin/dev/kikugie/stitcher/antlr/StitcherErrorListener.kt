package dev.kikugie.stitcher.antlr

import dev.kikugie.stitcher.antlr.StitcherLexer.*
import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.location
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.parser.SYNTAX_GROUP
import org.antlr.v4.runtime.*

internal class StitcherErrorListener(val problems: FileProblemReporter) : BaseErrorListener() {
    override fun syntaxError(recognizer: Recognizer<*, *>, symbol: Any?, line: Int, charPositionInLine: Int, msg: String?, e: RecognitionException?) {
        val index = if (symbol is AntlrToken) symbol.startIndex else problems.index.reverse(line - 1, charPositionInLine)
        problems.report(SYNTAX_GROUP.child("antlr-error"), problems.location(line - 1, charPositionInLine, index)) {
            val (primary, secondary) = unmangle(msg)
            label = primary ?: e?.message ?: "Unknown error"
            if (secondary != null) details = secondary
        }
    }

    private fun unmangle(message: String?): Pair<String?, String?> {
        val split = message?.split('\n', limit = 2) ?: return null to null
        return split.firstOrNull() to split.lastOrNull()
    }
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
        val options = buildOptions(recognizer, err.expectedTokens.toSet())
        val message = "Mismatched input${getTokenErrorDisplay(err.offendingToken)}\nExpected tokens: [${options.joinToString()}]"
        recognizer.notifyErrorListeners(err.offendingToken, message, err)
    }

    private fun buildOptions(parser: Parser, set: MutableSet<Int>): Set<String> = buildSet {
        for (intr in INTERPRETERS) intr.consume(set)?.let(this::add)
        for (rem in set) this += parser.vocabulary.getDisplayName(rem)
    }

    private fun interface TokenInterpreter {
        fun consume(variants: MutableSet<Int>): String?
    }

    private fun MutableSet<Int>.consume(description: String, type: Int): String? = description.takeIf { remove(type) }
    private fun MutableSet<Int>.consume(description: String, vararg types: Int): String? = description.takeIf { removeAll(types.asIterable()) }
}
