package dev.kikugie.stitcher.transformer.edit.impl

import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.token.type.BuiltinTokenType
import dev.kikugie.stitcher.transformer.edit.CommentingStrategy
import dev.kikugie.stitcher.transformer.edit.countLeadingIndent
import dev.kikugie.stitcher.transformer.edit.hasLineContentAfter
import dev.kikugie.stitcher.transformer.edit.spansMultipleLines
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry

class JavaLikeCommentingStrategy(val flatten: Boolean) : CommentingStrategy {
    override fun comment(config: ConfigurationRegistry, tracker: ModificationTracker, tokens: Sequence<LeafToken>): Unit = with(tracker) {
        val offset = start + document.countLeadingIndent(start, end)
        if (document.canUseLineComment(start, end))
            tracker.insert(offset, "//")
        else {
            tracker.insert(offset, "/*")
            tracker.insert(end, "*/")
            if (flatten) flattenComments(tokens, tracker)
        }
    }

    private fun flattenComments(tokens: Sequence<LeafToken>, tracker: ModificationTracker) {
        for (token in tokens) when (token.type as? BuiltinTokenType) {
            BuiltinTokenType.COMMENT_OPEN -> if (token.text == "/*")
                tracker.replace(token.start + 1, token.end, "^")

            BuiltinTokenType.COMMENT_CLOSE -> if (token.text == "*/")
                tracker.replace(token.start, token.end - 1, "^")

            else -> {}
        }
    }

    companion object {
        /** Java-style commenting, flattening nested block comments. */
        val Java: JavaLikeCommentingStrategy = JavaLikeCommentingStrategy(flatten = true)

        /** Kotlin-style commenting; nested block comments are preserved as-is. */
        val Kotlin: JavaLikeCommentingStrategy = JavaLikeCommentingStrategy(flatten = false)
    }
}

private const val MARKERS: String = "?$~"

private fun Document.canUseLineComment(start: Int, end: Int): Boolean =
    !spansMultipleLines(start, end) && !hasLineContentAfter(start, end) && !containsNestedMarker(start, end)

private fun Document.containsNestedMarker(start: Int, end: Int): Boolean {
    for (index in start..end - 3) {
        if (get(index) != '/') continue
        if (get(index + 1) != '/' && get(index + 1) != '*') continue
        if (get(index + 2) in MARKERS) return true
    }
    return false
}
