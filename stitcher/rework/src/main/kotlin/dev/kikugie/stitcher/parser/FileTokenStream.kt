package dev.kikugie.stitcher.parser

import dev.kikugie.antlr.util.LC
import dev.kikugie.antlr.util.advance
import dev.kikugie.antlr.util.endIndex
import dev.kikugie.commons.takeAs
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.InlineCharStream
import dev.kikugie.stitcher.antlr.InlineTokenFactory
import dev.kikugie.stitcher.antlr.StitcherErrorListener
import dev.kikugie.stitcher.antlr.StitcherErrorStrategy
import dev.kikugie.stitcher.antlr.StitcherLexer
import dev.kikugie.stitcher.antlr.StitcherParser
import dev.kikugie.stitcher.antlr.errorListener
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.issue.BailException
import dev.kikugie.stitcher.document.issue.LAYOUT_GROUP
import dev.kikugie.stitcher.document.issue.ProblemReporter
import dev.kikugie.stitcher.document.issue.bail
import dev.kikugie.stitcher.document.issue.report
import dev.kikugie.stitcher.document.issue.runThrowing
import dev.kikugie.stitcher.document.reference
import dev.kikugie.stitcher.token.BlockToken
import dev.kikugie.stitcher.token.DefinitionToken
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.token.eval.StitcherTokenBuilder
import dev.kikugie.stitcher.token.toLeaf
import dev.kikugie.stitcher.token.type.BuiltinTokenType
import dev.kikugie.stitcher.token.type.StitcherTokenType
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.TokenStream
import kotlin.use

class FileTokenStream(val document: Document, val stream: TokenStream) : Iterator<BlockToken> {
    private val listener: StitcherErrorListener = StitcherErrorListener(document)

    override fun hasNext(): Boolean = stream.LA(1) != AntlrToken.EOF

    override fun next(): BlockToken = when (StitcherTokenType.of(stream.LA(1))) {
        BuiltinTokenType.CONTENT -> processContent(stream.advance())
        BuiltinTokenType.COMMENT_OPEN -> processComment(stream.advance(), stream.advance(), stream.advance())
        else -> document.bailUnknownToken(stream.advance())
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

    private fun ProblemReporter.bailUnknownToken(token: AntlrToken): Nothing = bail(LAYOUT_GROUP + "unexpected-token") {
        at = document.reference(token.startIndex, token.endIndex)

        label = "Unknown file layout token"
        info = "Token type ${token.type} was not expected to appear ꒰•́ ｡ •̀꒱?."
        hint = """
        Please report this to https://codeberg.org/stonecutter/stonecutter/issues
        with the problematic file and stacktrace attached.
    """.trimIndent()
    }

    private fun ProblemReporter.reportParserError(token: AntlrToken, err: Throwable): Unit = report(LAYOUT_GROUP + "parser-failure") {
        at = document.reference(token.startIndex, token.endIndex)

        label = "Invalid expression"
        info = "Parser tried to do its job and had a burnout ╮(⊙_ʖ⊙)╭."
        hint = """
        Please report this to https://codeberg.org/stonecutter/stonecutter/issues
        with the problematic file and stacktrace attached.
    """.trimIndent()
        error = err
    }
}

private fun Char.isMarker(): Boolean = when(this) {
    '?', '$', '~' -> true
    else -> false
}
