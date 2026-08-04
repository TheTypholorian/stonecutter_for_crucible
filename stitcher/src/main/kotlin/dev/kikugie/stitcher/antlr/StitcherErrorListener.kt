package dev.kikugie.stitcher.antlr

import dev.kikugie.antlr.util.endIndex
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.issue.ProblemID
import dev.kikugie.stitcher.document.issue.SYNTAX_GROUP
import dev.kikugie.stitcher.document.issue.report
import dev.kikugie.stitcher.document.reference
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer

private val ANTLR_PROBLEM_ID: ProblemID = SYNTAX_GROUP + "antlr-error"

class StitcherErrorListener(val document: Document) : BaseErrorListener() {
    override fun syntaxError(
        recognizer: Recognizer<*, *>, symbol: Any?, line: Int, charPositionInLine: Int, msg: String?, e: RecognitionException?
    ): Unit = document.report(ANTLR_PROBLEM_ID) {
        at = when (symbol) {
            is AntlrToken -> document.reference(symbol.startIndex, symbol.endIndex)
            else -> document.reference(document.indexOf(line - 1, charPositionInLine))
        }

        val (primary, secondary) = unmangle(msg)
        label = primary ?: e?.message ?: "Unknown error"
        info = secondary
        error = e
    }

    private fun unmangle(message: String?): Pair<String?, String?> {
        val split: List<String> = message?.split('\n', limit = 2).orEmpty()
        return split.firstOrNull() to split.lastOrNull()
    }
}