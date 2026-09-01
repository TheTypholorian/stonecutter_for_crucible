package matcher

import de.infix.testBalloon.framework.core.Test
import dev.kikugie.semver.data.Version
import dev.kikugie.stitcher.Stitcher
import dev.kikugie.stitcher.StitcherParameters
import dev.kikugie.stitcher.antlr.HashStyleScanner
import dev.kikugie.stitcher.antlr.HashStyleScanner.Tokens.HASH_COMMENT_END
import dev.kikugie.stitcher.antlr.HashStyleScanner.Tokens.HASH_COMMENT_START
import dev.kikugie.stitcher.antlr.SlashStyleScanner
import dev.kikugie.stitcher.antlr.SlashStyleScanner.Tokens.SLASH_COMMENT_END
import dev.kikugie.stitcher.antlr.SlashStyleScanner.Tokens.SLASH_COMMENT_START
import dev.kikugie.stitcher.antlr.SlashStyleScanner.Tokens.STAR_COMMENT_END
import dev.kikugie.stitcher.antlr.SlashStyleScanner.Tokens.STAR_COMMENT_START
import dev.kikugie.stitcher.antlr.StitcherErrorListener
import dev.kikugie.stitcher.antlr.charStream
import dev.kikugie.stitcher.antlr.errorListener
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.SourceDocument
import dev.kikugie.stitcher.document.issue.DefaultProblemReporter
import dev.kikugie.stitcher.document.issue.ProblemReport.Severity
import dev.kikugie.stitcher.document.issue.ProblemReporter
import dev.kikugie.stitcher.document.issue.output.FullProblemFormatter
import dev.kikugie.stitcher.document.issue.output.ShortProblemFormatter
import dev.kikugie.stitcher.document.issue.runThrowing
import dev.kikugie.stitcher.parser.FileAstParser
import dev.kikugie.stitcher.parser.FileTokenStream
import dev.kikugie.stitcher.parser.ScannerAdapter
import dev.kikugie.stitcher.token.BlockToken
import dev.kikugie.stitcher.token.appendTo
import dev.kikugie.stitcher.token.type.BuiltinTokenType
import dev.kikugie.stitcher.transformer.edit.CommentingStrategy
import dev.kikugie.stitcher.transformer.edit.SwappingStrategy
import dev.kikugie.stitcher.transformer.edit.UncommentingStrategy
import dev.kikugie.stitcher.transformer.edit.impl.SlashStarUncommentingStrategy
import dev.kikugie.stitcher.transformer.edit.impl.StandardSwappingStrategy
import dev.kikugie.stitcher.transformer.edit.impl.StarCommentingStrategy
import dev.kikugie.stonecutter.model.Identifier
import dev.kikugie.stonecutter.model.process.*
import org.antlr.v4.runtime.*
import org.intellij.lang.annotations.Language
import kotlin.io.path.Path

fun immediateThrow(): ProblemReporter = DefaultProblemReporter({
    System.err.println(it.accept(FullProblemFormatter))
    if (it.severity == Severity.ERROR) throw AssertionError(it.accept(ShortProblemFormatter))
})

fun Test.ExecutionScope.parseSnippet(value: String, reporter: ProblemReporter = immediateThrow()): BlockToken.Root {
    val document = SourceDocument(Path("$testElementPath"), value, reporter)
    val source = Scanner.Java.create(value.charStream(), document).apply {
        scanner.errorListener = StitcherErrorListener(document)
    }

    return document.runThrowing {
        val stream = FileTokenStream(document, UnbufferedTokenStream<Token>(source))
        FileAstParser.parse(stream)
    }
}

fun Test.ExecutionScope.assemble(
    value: String, params: StitcherParameters = params(), reporter: ProblemReporter = immediateThrow()
): String = buildString {
    Stitcher.process(SourceDocument(Path("$testElementPath"), value, reporter), params).appendTo(this)
}

inline fun params(action: StitcherParametersBuilder.() -> Unit = {}): StitcherParameters =
    StitcherParametersBuilder().apply(action).buildParams()

inline fun vars(action: ProcessParametersBuilder.() -> Unit = {}): ProcessParameters =
    ProcessParametersBuilder().apply(action).buildVars()

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

object Uncommenter {
    val Java: UncommentingStrategy = SlashStarUncommentingStrategy

    val Lines: UncommentingStrategy = UncommentingStrategy { tracker, tokens ->
        for (it in tokens) if (it.type == BuiltinTokenType.COMMENT_OPEN)
            tracker.delete(it)
    }
}

object Commenter {
    val Java: CommentingStrategy = StarCommentingStrategy.Java
    val Kotlin: CommentingStrategy = StarCommentingStrategy.Kotlin
}

object Swapper {
    val Standard: SwappingStrategy = StandardSwappingStrategy
}

@DslMarker
@Retention(AnnotationRetention.SOURCE)
private annotation class VarsBuilderDsl

@VarsBuilderDsl
class StitcherParametersBuilder : ProcessParametersBuilder() {
    val registry: MutableMap<ConfigurationFlag<*>, Any> = HashMap()

    var scanner: ScannerAdapter.Factory = Scanner.Java
    var commenter: CommentingStrategy = Commenter.Java
    var uncommenter: UncommentingStrategy = Uncommenter.Java
    var swapper: SwappingStrategy = Swapper.Standard

    fun buildParams(): StitcherParameters = StitcherParameters(
        buildVars(), ConfigurationRegistry(registry),
        scanner, commenter, uncommenter, swapper
    )
}

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

    fun buildVars(): ProcessParameters = ProcessParameters(constants, targets, swaps, replacements)
}
