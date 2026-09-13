package dev.kikugie.stitcher.transformer.edit.impl

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.token.type.BuiltinTokenType
import dev.kikugie.stitcher.transformer.edit.CommentingStrategy
import dev.kikugie.stitcher.transformer.edit.countLeadingIndent
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry

object XmlCommentingStrategy : CommentingStrategy {
    override fun comment(config: ConfigurationRegistry, tracker: ModificationTracker, tokens: Sequence<LeafToken>): Unit = with(tracker) {
        insert(start + document.countLeadingIndent(start, end), "<!--")
        insert(end, "-->")
        flattenComments(tokens, tracker)
    }

    private fun flattenComments(tokens: Sequence<LeafToken>, tracker: ModificationTracker) {
        for (token in tokens) when (token.type as? BuiltinTokenType) {
            // <!-- -> <!^^
            BuiltinTokenType.COMMENT_OPEN -> if (token.text == "<!--")
                tracker.replace(token.start + 2, token.end, "^^")

            // --> -> ^^>
            BuiltinTokenType.COMMENT_CLOSE -> if (token.text == "-->")
                tracker.replace(token.start, token.end - 1, "^^")

            else -> {}
        }
    }

    private fun readResolve(): Any = this
}
