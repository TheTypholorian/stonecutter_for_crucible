package dev.kikugie.stitcher.transformer.edit.impl

import dev.kikugie.antlr.util.advance
import dev.kikugie.antlr.util.endIndex
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.XmlCommentEscaper
import dev.kikugie.stitcher.antlr.XmlCommentEscaper.Tokens
import dev.kikugie.stitcher.antlr.charStream
import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.token.type.BuiltinTokenType
import dev.kikugie.stitcher.transformer.edit.UncommentingStrategy
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import org.antlr.v4.runtime.CommonTokenStream
import kotlin.io.path.absolutePathString

object XmlUncommentingStrategy : UncommentingStrategy {
    override fun uncomment(config: ConfigurationRegistry, tracker: ModificationTracker, tokens: Sequence<LeafToken>) {
        for (leaf in tokens) when (leaf.type as? BuiltinTokenType ?: BuiltinTokenType.ROOT) {
            BuiltinTokenType.COMMENT_OPEN -> tracker.delete(leaf)
            BuiltinTokenType.COMMENT_BODY -> processEscapedComment(tracker, leaf)
            BuiltinTokenType.COMMENT_CLOSE -> tracker.delete(leaf)
            else -> {}
        }
    }

    private fun processEscapedComment(tracker: ModificationTracker, leaf: LeafToken) {
        val tokens = leaf.text.charStream(leaf.ref.document.file.absolutePathString())
            .let { CommonTokenStream(XmlCommentEscaper(it)) }
        var depth = 0
        while (true) depth = when (val it = tokens.LA(1)) {
            AntlrToken.EOF -> break
            Tokens.XML_ESCAPED_START -> tracker.handleOpener(tokens.advance(), depth, leaf.start)
            Tokens.XML_ESCAPED_END -> tracker.handleCloser(tokens.advance(), depth, leaf.start)
            else -> error("Invalid token type ${XmlCommentEscaper.VOCABULARY.getDisplayName(it)}")
        }
    }

    // <!^^ -> <!--
    private fun ModificationTracker.handleOpener(token: AntlrToken, depth: Int, offset: Int): Int = when (depth) {
        0 -> replace(token.startIndex + offset + 2, token.endIndex + offset, "--").let { 1 }
        else -> depth + 1
    }

    // ^^> -> -->
    private fun ModificationTracker.handleCloser(token: AntlrToken, depth: Int, offset: Int): Int = when (depth) {
        0 -> 0
        1 -> replace(token.startIndex + offset, token.endIndex + offset - 1, "--").let { 0 }
        else -> depth - 1
    }

    private fun readResolve(): Any = this
}
