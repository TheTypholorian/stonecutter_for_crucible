package dev.kikugie.stitcher.antlr

import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.ProblemLocation
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.parser.SYNTAX_GROUP
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer

// TODO: Finish this shi
internal class InlineErrorListener(val offset: ProblemLocation, val problems: FileProblemReporter) : BaseErrorListener() {
    override fun syntaxError(recognizer: Recognizer<*, *>, symbol: Any?, line: Int, charPositionInLine: Int, msg: String?, e: RecognitionException?) {
        val location = ProblemLocation(problems.file, line - 1, charPositionInLine)
        problems.report(SYNTAX_GROUP.child("antlr-error"), offset + location) {
            label = msg ?: e?.message ?: "An unknown error has occurred!"
        }
    }
}