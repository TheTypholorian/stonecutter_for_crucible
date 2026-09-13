package dev.kikugie.stitcher.transformer.edit.impl

import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.document.maxIndex
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.token.type.BuiltinTokenType
import dev.kikugie.stitcher.transformer.edit.CommentingStrategy

class JavaLikeCommentingStrategy(val flatten: Boolean) : CommentingStrategy {
    override fun comment(tracker: ModificationTracker, tokens: Sequence<LeafToken>): Unit = with(tracker) {
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

private fun Document.canUseLineComment(start: Int, end: Int): Boolean =
    !spansMultipleLines(start, end) && !hasLineContentAfter(start, end)

private fun Document.spansMultipleLines(start: Int, end: Int): Boolean {
    val content: CharSequence = subSequence(start, end).trim()
    return '\n' in content
}

private fun Document.hasLineContentAfter(start: Int, end: Int): Boolean {
    for (index in end until maxIndex()) when (get(index)) {
        ' ', '\t' -> continue
        '\n' -> break
        else -> return true
    }
    return false
}

private fun Document.countLeadingIndent(start: Int, end: Int): Int {
    for (index in start until end) when (get(index)) {
        ' ', '\t', '\n' -> continue
        else -> return index - start
    }
    return end - start
}
