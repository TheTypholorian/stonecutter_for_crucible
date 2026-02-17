package dev.kikugie.stitcher.antlr

import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.location
import dev.kikugie.stitcher.issue.parser.ParserErrorInterpreter
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.parser.SYNTAX_GROUP
import org.antlr.v4.runtime.*

// TODO: Finish this shi
internal class StitcherErrorListener(val problems: FileProblemReporter) : BaseErrorListener() {
    override fun syntaxError(recognizer: Recognizer<*, *>, symbol: Any?, line: Int, charPositionInLine: Int, msg: String?, e: RecognitionException?) {
        val index = if (symbol is AntlrToken) symbol.startIndex else problems.index.reverse(line - 1, charPositionInLine)
        problems.report(SYNTAX_GROUP.child("antlr-error"), problems.location(line - 1, charPositionInLine, index)) {
            label = msg ?: e?.message ?: "An unknown error has occurred!"
        }
    }
}

internal object StitcherErrorStrategy : DefaultErrorStrategy() {
    private val mismatchInterpreters: List<ParserErrorInterpreter> = listOf(
//        DefaultErrorInterpreters.EMPTY_CONDITION,
//        DefaultErrorInterpreters.MISSING_CLOSER,
    )

    override fun reportInputMismatch(recognizer: Parser, err: InputMismatchException) {
        val message = mismatchInterpreters.firstNotNullOfOrNull { it.handle(recognizer, err) }
        if (message != null) recognizer.notifyErrorListeners(err.offendingToken, message.id, err)
        else super.reportInputMismatch(recognizer, err)
    }
}
