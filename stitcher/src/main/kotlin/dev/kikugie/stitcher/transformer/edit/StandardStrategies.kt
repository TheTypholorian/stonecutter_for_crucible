package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.antlr.util.advance
import dev.kikugie.antlr.util.endIndex
import dev.kikugie.antlr.util.length
import dev.kikugie.commons.text.countMatching
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.StarCommentEscaper
import dev.kikugie.stitcher.antlr.StarCommentEscaper.Tokens
import dev.kikugie.stitcher.document.ModificationTracker
import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.impl.LeafToken
import dev.kikugie.stitcher.util.WHITESPACES
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream

object StarNestCommentingStrategy : CommentingStrategy {
    override fun comment(tracker: ModificationTracker, tokens: Sequence<LeafToken>) {
        tracker.insert(tokens.countWhiteSpaces(), "/*")
        tracker.insert(tracker.length, "*/")
    }

    private fun readResolve(): Any = this
}

object StarFlatCommentingStrategy : CommentingStrategy {
    override fun comment(tracker: ModificationTracker, tokens: Sequence<LeafToken>) {
        tracker.insert(tokens.countWhiteSpaces(), "/*")
        tracker.insert(tracker.length, "*/")

        for (token in tokens) when (token.type) {
            NativeStitcherType.COMMENT_OPEN if (token.text == "/*") ->
                tracker.replace(token.start + 1, token.end, "^")
            NativeStitcherType.COMMENT_CLOSE if (token.text == "*/") ->
                tracker.replace(token.start, token.end - 1, "^")
            else -> {}
        }
    }

    private fun readResolve(): Any = this
}

object SlashStarUncommentingStrategy : UncommentingStrategy {
    override fun uncomment(tracker: ModificationTracker, tokens: Sequence<LeafToken>) {
        for (token in tokens) when (token.type) {
            NativeStitcherType.COMMENT_OPEN -> tracker.delete(token)
            NativeStitcherType.COMMENT_CLOSE if ('\n' !in token.text) -> tracker.delete(token)
            NativeStitcherType.COMMENT_CLOSE if (token.text != "\n") -> tracker.replace(token, "\n")
            NativeStitcherType.COMMENT_BODY -> tracker.processEscapedComment(token)
            else -> {}
        }
    }

    private fun ModificationTracker.processEscapedComment(token: LeafToken) {
        val tokens = StarCommentEscaper(CharStreams.fromString(token.text))
            .let(::CommonTokenStream)
        var depth = 0
        while (true) depth = when(val it = tokens.LA(1)) {
            Tokens.TILDA_COMMENT_START -> handleOpener(tokens.advance(), depth, token.start)
            Tokens.TILDA_COMMENT_END -> handleCloser(tokens.advance(), depth, token.start)
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

    private fun readResolve(): Any = this
}

private fun Sequence<LeafToken>.countWhiteSpaces(): Int {
    val first = firstOrNull()?.takeIf { it.type == NativeStitcherType.CONTENT }
        ?: return 0
    return first.text.countMatching(*WHITESPACES)
}
