package dev.kikugie.stitcher.parser

import dev.kikugie.antlr.util.LC
import dev.kikugie.antlr.util.advance
import dev.kikugie.antlr.util.endIndex
import dev.kikugie.commons.takeAs
import dev.kikugie.stitcher.antlr.*
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.issue.*
import dev.kikugie.stitcher.document.reference
import dev.kikugie.stitcher.token.BlockToken
import dev.kikugie.stitcher.token.DefinitionToken
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.token.eval.StitcherTokenBuilder
import dev.kikugie.stitcher.token.toLeaf
import dev.kikugie.stitcher.token.type.BuiltinTokenType.COMMENT_OPEN
import dev.kikugie.stitcher.token.type.BuiltinTokenType.CONTENT
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.TokenStream

private const val MAX_PREVIOUS_TOKENS: Int = 8

class FileTokenStream(val document: Document, val stream: TokenStream) {
    private val listener: StitcherErrorListener = StitcherErrorListener(document)
    private val buffer: ArrayDeque<BlockToken> = ArrayDeque()
    private var base: Int = 0
    private var cursor: Int = 0
    private var exhausted: Boolean = false

    fun peek(offset: Int): BlockToken? {
        require(offset != 0) { "Peek offset must be non-zero" }
        if (offset < -MAX_PREVIOUS_TOKENS) return null

        val target = cursor + if (offset > 0) offset - 1 else offset
        return if (target < base || !fill(target)) null else buffer[target - base]
    }

    fun consume() {
        cursor++
        while (buffer.isNotEmpty() && base < cursor - MAX_PREVIOUS_TOKENS) {
            buffer.removeFirst()
            base++
        }
    }

    fun advance(): BlockToken? = peek(1).also {
        if (it != null) consume()
    }

    fun insert(token: BlockToken) {
        buffer.add(cursor - base, token)
    }

    private fun fill(target: Int): Boolean {
        while (!exhausted && base + buffer.size <= target)
            buffer += processNext() ?: run { exhausted = true; break }
        return target < base + buffer.size
    }

    private tailrec fun processNext(): BlockToken? = when (stream.LA(1)) {
        AntlrToken.EOF -> null
        CONTENT.value -> processContent(stream.advance())
        COMMENT_OPEN.value -> processComment(stream.advance(), stream.advance(), stream.advance())
        else -> { document.reportUnknownToken(stream.advance()); processNext() }
    }

    private fun processContent(token: AntlrToken): BlockToken.Content =
        BlockToken.Content(token.toLeaf(document))

    private fun processComment(opener: AntlrToken, body: AntlrToken, closer: AntlrToken): BlockToken {
        val comment = BlockToken.Comment(opener.toLeaf(document), body.toLeaf(document), closer.toLeaf(document))
        val (marker, definition) = parseCommentBody(body)
            ?: return comment
        return BlockToken.Code(comment, marker, definition, emptyList())
    }

    private fun parseCommentBody(body: AntlrToken): Pair<LeafToken, DefinitionToken>? = InlineCharStream(body).use { input ->
        if (!input.LC(1).isMarker()) return@use null

        val lexer = StitcherLexer(input).apply {
            errorListener = listener
            tokenFactory = InlineTokenFactory(body.line - 1, body.charPositionInLine)
        }
        val parser = StitcherParser(CommonTokenStream(lexer)).apply {
            errorListener = listener
            errorHandler = StitcherErrorStrategy
        }
        parser.tryBuildDefinition(body)
    }

    private fun StitcherParser.tryBuildDefinition(body: AntlrToken): Pair<LeafToken, DefinitionToken>? = try {
        val tree = document.runThrowing { definition() }
        tree.start.toLeaf(document) to tree.accept(StitcherTokenBuilder, document).takeAs()
    } catch (_: BailException) {
        null
    } catch (e: RuntimeException) {
        document.reportParserError(body, e)
        null
    }
}

context(stream: FileTokenStream)
private fun ProblemReporter.reportUnknownToken(token: AntlrToken): Unit = report(LAYOUT_GROUP + "unexpected-token") {
    at = stream.document.reference(token.startIndex, token.endIndex)

    label = "Unknown file layout token"
    info = "Token type ${token.type} was not expected to appear ꒰•́ ｡ •̀꒱?."
    hint = """
        Please report this to https://codeberg.org/stonecutter/stonecutter/issues
        with the problematic file and stacktrace attached.
    """.trimIndent()
}

context(stream: FileTokenStream)
private fun ProblemReporter.reportParserError(token: AntlrToken, err: Throwable): Unit = report(LAYOUT_GROUP + "parser-failure") {
    at = stream.document.reference(token.startIndex, token.endIndex)

    label = "Invalid expression"
    info = "Parser tried to do its job and had a burnout ╮(⊙_ʖ⊙)╭."
    hint = """
        Please report this to https://codeberg.org/stonecutter/stonecutter/issues
        with the problematic file and stacktrace attached.
    """.trimIndent()
    error = err
}

private fun Char.isMarker(): Boolean = when (this) {
    '?', '$', '~' -> true
    else -> false
}
