package dev.kikugie.stitcher.parser

import dev.kikugie.antlr.util.LC
import dev.kikugie.antlr.util.advance
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.InlineCharStream
import dev.kikugie.stitcher.antlr.InlineTokenFactory
import dev.kikugie.stitcher.antlr.StitcherErrorListener
import dev.kikugie.stitcher.antlr.StitcherErrorStrategy
import dev.kikugie.stitcher.antlr.StitcherLexer
import dev.kikugie.stitcher.antlr.StitcherParser
import dev.kikugie.stitcher.antlr.acceptAs
import dev.kikugie.stitcher.antlr.lineIndex
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.locate
import dev.kikugie.stitcher.issue.BailException
import dev.kikugie.stitcher.issue.ProblemReporter
import dev.kikugie.stitcher.issue.bail
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.issue.runThrowing
import dev.kikugie.stitcher.token.LAYOUT_GROUP
import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.StitcherType
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.LeafToken
import dev.kikugie.stitcher.token.toLeafToken
import dev.kikugie.stitcher.util.errorListener
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.TokenStream


class FileTokenStream(val document: Document, val stream: TokenStream) : Iterator<BlockToken> {
    private val listener: StitcherErrorListener by lazy { StitcherErrorListener(document) }

    override fun hasNext(): Boolean = stream.LA(1) != AntlrToken.EOF

    override fun next(): BlockToken = when (StitcherType.of(stream.LA(1))) {
        NativeStitcherType.CONTENT -> processContent(stream.advance())
        NativeStitcherType.COMMENT_OPEN -> processComment(stream.advance(), stream.advance(), stream.advance())
        else -> document.bailUnknownToken(stream.advance())
    }

    private fun processContent(token: AntlrToken): BlockToken.Content =
        BlockToken.Content(token.toLeafToken(document))

    private fun processComment(opener: AntlrToken, body: AntlrToken, closer: AntlrToken): BlockToken {
        val comment = BlockToken.Comment(opener.toLeafToken(document), body.toLeafToken(document), closer.toLeafToken(document))
        val (marker, definition) = parseCommentBody(body) ?: return comment
        return BlockToken.Code(comment, marker, definition)
    }

    private fun parseCommentBody(body: AntlrToken): Pair<LeafToken, DefinitionToken>? = InlineCharStream(body).use { input ->
        if (!input.LC(1).isMarker()) return@use null

        val lexer = StitcherLexer(input).apply {
            errorListener = listener
            tokenFactory = InlineTokenFactory(body.lineIndex, body.charPositionInLine)
        }
        val parser = StitcherParser(CommonTokenStream(lexer)).apply {
            errorListener = listener
            errorHandler = StitcherErrorStrategy
        }
        parser.tryBuildDefinition(body)
    }

    private fun StitcherParser.tryBuildDefinition(body: AntlrToken): Pair<LeafToken, DefinitionToken>? = try {
        val tree = document.runThrowing { definition() }
        tree.start.toLeafToken(document) to tree.acceptAs(TokenBuilder, document)
    } catch (_: BailException) {
        null
    } catch (e: RuntimeException) {
        document.reportParserError(body, e)
        null
    }
}

private fun Char.isMarker(): Boolean = when(this) {
    '?', '$', '~' -> true
    else -> false
}

private fun ProblemReporter.bailUnknownToken(token: AntlrToken): Nothing = bail(
    LAYOUT_GROUP + "unexpected-token"
) {
    loc = doc.locate(token)

    label = "Unknown file layout token"
    info = "Token type ${token.type} was not expected to appear ꒰•́ ｡ •̀꒱?."
    hint = """
        Please report this to https://codeberg.org/stonecutter/stonecutter/issues
        with the problematic file and stacktrace attached.
    """.trimIndent()
}

private fun ProblemReporter.reportParserError(body: AntlrToken, error: Throwable): Unit = report(
    LAYOUT_GROUP + "parser-failure"
) {
    loc = doc.locate(body)
    label = "Invalid expression"
    info = "Parser tried to do its job and had a burnout ╮(⊙_ʖ⊙)╭."
    hint = """
        Please report this to https://codeberg.org/stonecutter/stonecutter/issues
        with the problematic file and stacktrace attached.
    """.trimIndent()
    err = error
}
