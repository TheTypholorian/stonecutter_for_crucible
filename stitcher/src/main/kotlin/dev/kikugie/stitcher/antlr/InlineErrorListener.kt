package dev.kikugie.stitcher.antlr

import dev.kikugie.stitcher.issue.ProblemLocation
import dev.kikugie.stitcher.issue.ProblemID
import dev.kikugie.stitcher.issue.ProblemReporter
import dev.kikugie.stitcher.issue.report
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer

private val SYNTAX_ERROR: ProblemID = ProblemID.SYNTAX_GROUP.create("antlr-error")

// TODO: Finish this shi
internal class InlineErrorListener(val offset: ProblemLocation, val problems: ProblemReporter) : BaseErrorListener() {
    override fun syntaxError(recognizer: Recognizer<*, *>, symbol: Any?, line: Int, charPositionInLine: Int, msg: String?, e: RecognitionException?) {
        val location = ProblemLocation(line, charPositionInLine + 1)
        problems.report(SYNTAX_ERROR, offset.resolve(location)) {
            label = msg ?: e?.message ?: "An unknown error has occurred!"
        }
    }
}