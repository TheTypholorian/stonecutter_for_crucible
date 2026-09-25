package dev.kikugie.stitcher.transformer.edit.impl

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.token.type.BuiltinTokenType
import dev.kikugie.stitcher.transformer.edit.CommentingStrategy
import dev.kikugie.stitcher.transformer.edit.countLeadingIndent
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry

object XmlCommentingStrategy : CommentingStrategy {
    private const val EOF: Char = '\u0000'

    override fun comment(config: ConfigurationRegistry, tracker: ModificationTracker, tokens: Sequence<LeafToken>): Unit = with(tracker) {
        insert(start + document.countLeadingIndent(start, end), "<!--")
        insert(end, "-->")

        val list = tokens.toList()
        flattenComments(list, tracker)
        escapeDashes(list, tracker)
    }

    private fun flattenComments(tokens: List<LeafToken>, tracker: ModificationTracker) {
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

    private fun escapeDashes(tokens: List<LeafToken>, tracker: ModificationTracker) {
        for ((index, token) in tokens.withIndex()) {
            if (token.isOpener() || token.isCloser()) continue

            val text = token.text
            for (i in text.indices) if (text[i] == '-' && followedByDashOrEnd(text, i, tokens.getOrNull(index + 1)))
                tracker.replace(token.start + i, token.start + i + 1, "-^")
        }
    }

    private fun LeafToken.isOpener(): Boolean =
        type == BuiltinTokenType.COMMENT_OPEN && text == "<!--"

    private fun LeafToken.isCloser(): Boolean =
        type == BuiltinTokenType.COMMENT_CLOSE && text == "-->"

    private fun followedByDashOrEnd(text: String, index: Int, next: LeafToken?): Boolean = when (followingChar(text, index, next)) {
        EOF, '-', '^' -> true
        else -> false
    }

    private fun followingChar(text: String, index: Int, next: LeafToken?): Char = when {
        index + 1 < text.length -> text[index + 1]
        next == null -> EOF
        next.isCloser() -> '^'
        next.isOpener() -> '<'
        next.text.isEmpty() -> EOF
        else -> next.text.first()
    }

    private fun readResolve(): Any = this
}
