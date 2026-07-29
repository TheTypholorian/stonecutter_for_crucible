package matcher

import de.infix.testBalloon.framework.core.Test
import dev.kikugie.semver.data.Version
import dev.kikugie.stitcher.antlr.HashStyleScanner
import dev.kikugie.stitcher.antlr.HashStyleScanner.Tokens.HASH_COMMENT_END
import dev.kikugie.stitcher.antlr.HashStyleScanner.Tokens.HASH_COMMENT_START
import dev.kikugie.stitcher.antlr.SlashStyleScanner
import dev.kikugie.stitcher.antlr.SlashStyleScanner.Tokens.SLASH_COMMENT_END
import dev.kikugie.stitcher.antlr.SlashStyleScanner.Tokens.SLASH_COMMENT_START
import dev.kikugie.stitcher.antlr.SlashStyleScanner.Tokens.STAR_COMMENT_END
import dev.kikugie.stitcher.antlr.SlashStyleScanner.Tokens.STAR_COMMENT_START
import dev.kikugie.stitcher.antlr.StitcherErrorListener
import dev.kikugie.stitcher.antlr.errorListener
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.SourceDocument
import dev.kikugie.stitcher.document.issue.ProblemReport.Severity
import dev.kikugie.stitcher.document.issue.output.FullProblemFormatter
import dev.kikugie.stitcher.document.issue.output.ProblemConsumer
import dev.kikugie.stitcher.document.issue.output.ShortProblemFormatter
import dev.kikugie.stitcher.document.issue.runThrowing
import dev.kikugie.stitcher.parser.FileAstParser
import dev.kikugie.stitcher.parser.FileTokenStream
import dev.kikugie.stitcher.parser.ScannerAdapter
import dev.kikugie.stitcher.token.BlockToken
import dev.kikugie.stonecutter.model.Identifier
import dev.kikugie.stonecutter.model.process.PerlReplacement
import dev.kikugie.stonecutter.model.process.ProcessParameters
import dev.kikugie.stonecutter.model.process.RegexReplacement
import dev.kikugie.stonecutter.model.process.Replacement
import dev.kikugie.stonecutter.model.process.StringReplacement
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.UnbufferedTokenStream
import org.intellij.lang.annotations.Language
import kotlin.io.path.Path

val IMMEDIATE_THROW: ProblemConsumer = ProblemConsumer {
    System.err.println(it.accept(FullProblemFormatter))
    if (it.severity == Severity.ERROR) throw AssertionError(it.accept(ShortProblemFormatter))
}

fun Test.ExecutionScope.parseSnippet(value: String, consumer: ProblemConsumer = IMMEDIATE_THROW): BlockToken.Root {
    val document = SourceDocument(Path("$testElementPath"), value, consumer)
    val source = Scanner.Java.create(CharStreams.fromString(value), document).apply {
        scanner.errorListener = StitcherErrorListener(document)
    }

    return document.runThrowing {
        val stream = FileTokenStream(document, UnbufferedTokenStream<Token>(source))
        FileAstParser.parse(stream)
    }
}

inline fun vars(action: ProcessParametersBuilder.() -> Unit = {}): ProcessParameters =
    ProcessParametersBuilder().apply(action).build()

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

@DslMarker
@Retention(AnnotationRetention.SOURCE)
private annotation class VarsBuilderDsl

@VarsBuilderDsl
open class ProcessParametersBuilder {
    val constants: MutableMap<String, Boolean> = HashMap()
    val targets: MutableMap<String, Version> = HashMap()
    val swaps: MutableMap<String, String> = HashMap()
    val replacements: MutableList<Replacement> = ArrayList()

    init {
        constants["true"] = true
        constants["false"] = false
    }

    fun string(from: String, to: String, id: Identifier? = null, enabled: Boolean = false) {
        replacements += StringReplacement(from, to, id, enabled)
    }

    fun regex(@Language("RegExp") from: String, to: String, id: String? = null, enabled: Boolean = false) {
        replacements += RegexReplacement(from, to, 0, id, enabled)
    }

    fun perl(@Language("RegExp") from: String, to: String, id: String? = null, enabled: Boolean = false) {
        replacements += PerlReplacement(from, to, 0, id, enabled)
    }

    fun build(): ProcessParameters = ProcessParameters(constants, targets, swaps, replacements)
}
