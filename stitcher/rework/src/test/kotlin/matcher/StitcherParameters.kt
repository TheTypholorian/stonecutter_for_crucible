package matcher

import dev.kikugie.stitcher.antlr.HashStyleScanner
import dev.kikugie.stitcher.antlr.HashStyleScanner.Tokens.HASH_COMMENT_END
import dev.kikugie.stitcher.antlr.HashStyleScanner.Tokens.HASH_COMMENT_START
import dev.kikugie.stitcher.antlr.SlashStyleScanner
import dev.kikugie.stitcher.antlr.SlashStyleScanner.Tokens.SLASH_COMMENT_END
import dev.kikugie.stitcher.antlr.SlashStyleScanner.Tokens.SLASH_COMMENT_START
import dev.kikugie.stitcher.antlr.SlashStyleScanner.Tokens.STAR_COMMENT_END
import dev.kikugie.stitcher.antlr.SlashStyleScanner.Tokens.STAR_COMMENT_START
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.issue.ProblemReport.Severity
import dev.kikugie.stitcher.document.issue.output.FullProblemFormatter
import dev.kikugie.stitcher.document.issue.output.ProblemConsumer
import dev.kikugie.stitcher.document.issue.output.ShortProblemFormatter
import dev.kikugie.stitcher.parser.ScannerAdapter
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.Lexer

val IMMEDIATE_THROW: ProblemConsumer = ProblemConsumer {
    System.err.println(it.accept(FullProblemFormatter))
    if (it.severity == Severity.ERROR) throw AssertionError(it.accept(ShortProblemFormatter))
}

object Scanner {
    val Java: ScannerAdapter.Factory = Template(
        intArrayOf(SLASH_COMMENT_START, STAR_COMMENT_START),
        intArrayOf(SLASH_COMMENT_END, STAR_COMMENT_END),
        ::SlashStyleScanner
    )

    val Kotlin: ScannerAdapter.Factory = Template(
        intArrayOf(SLASH_COMMENT_START, STAR_COMMENT_START),
        intArrayOf(SLASH_COMMENT_END, STAR_COMMENT_END),
        { SlashStyleScanner(it).apply { nestMultiLineComments = true } }
    )

    val Hash: ScannerAdapter.Factory = Template(
        intArrayOf(HASH_COMMENT_START),
        intArrayOf(HASH_COMMENT_END),
        ::HashStyleScanner
    )

    private class Template(
        val openers: IntArray,
        val closers: IntArray,
        val constructor: (CharStream) -> Lexer,
    ) : ScannerAdapter.Factory {
        override fun create(input: CharStream, document: Document): ScannerAdapter =
            ScannerAdapter(constructor(input), openers, closers, document)
    }
}