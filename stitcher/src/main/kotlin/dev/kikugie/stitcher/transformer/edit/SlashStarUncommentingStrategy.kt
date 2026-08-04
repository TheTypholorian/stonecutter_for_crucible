package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.antlr.util.advance
import dev.kikugie.antlr.util.endIndex
import dev.kikugie.antlr.util.length
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.StarCommentEscaper
import dev.kikugie.stitcher.antlr.StarCommentEscaper.Tokens
import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.token.type.BuiltinTokenType
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import kotlin.io.path.absolutePathString

object SlashStarUncommentingStrategy : UncommentingStrategy {
    override fun uncomment(tracker: ModificationTracker, tokens: Sequence<LeafToken>) {
        var isMultiLine = false
        for (leaf in tokens) when (leaf.type as? BuiltinTokenType ?: BuiltinTokenType.ROOT) {
            BuiltinTokenType.COMMENT_OPEN -> tracker.delete(leaf).also { isMultiLine = leaf.text.startsWith("/*") }
            BuiltinTokenType.COMMENT_BODY -> if (isMultiLine) processEscapedComment(tracker, leaf)
            BuiltinTokenType.COMMENT_CLOSE -> if ('\n' in leaf.text) tracker.replace(leaf, "\n") else tracker.delete(leaf)
            else -> {}
        }
    }

    private fun processEscapedComment(tracker: ModificationTracker, leaf: LeafToken) {
        val tokens = CharStreams.fromString(leaf.text, leaf.ref.document.file.absolutePathString())
            .let { CommonTokenStream(StarCommentEscaper(it)) }
        var depth = 0
        while (true) depth = when(val it = tokens.LA(1)) {
            Tokens.TILDA_COMMENT_START -> tracker.handleOpener(tokens.advance(), depth, leaf.start)
            Tokens.TILDA_COMMENT_END -> tracker.handleCloser(tokens.advance(), depth, leaf.start)
            AntlrToken.EOF -> break
            else -> error("Invalid token type ${StarCommentEscaper.VOCABULARY.getDisplayName(it)}")
        }
    }

    private fun ModificationTracker.handleOpener(token: AntlrToken, depth: Int, offset: Int): Int = when(depth) {
        // /^¹²³ -> /*
        0 -> replace(token.startIndex + offset + 1, token.endIndex + offset, "*").let { 1 }
        else -> {
            // /^¹²³ -> /^
            if (token.length > 2) delete(token.startIndex + offset + 2, token.endIndex + offset)
            depth + 1
        }
    }

    private fun ModificationTracker.handleCloser(token: AntlrToken, depth: Int, offset: Int): Int = when(depth) {
        0 -> 0
        // ¹²³^/ -> */
        1 -> replace(token.startIndex + offset, token.endIndex + offset - 1, "*").let { 0 }
        else -> {
            // ¹²³^/ -> ^/
            if (token.length > 2) delete(token.startIndex + offset, token.endIndex + offset - 2)
            depth - 1
        }
    }
}