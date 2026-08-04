package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.token.type.BuiltinTokenType

/**
 * Default `/* */` commenting strategy for Java and Kotlin files.
 *
 * The shape is chosen from the surrounding document context available through
 * [ModificationTracker.document]:
 * - a single-line scope that ends its line is prefixed with `//`;
 * - anything else is wrapped with `/* */`.
 *
 * When [flatten] is `true` (the Java default), nested `/* */` delimiters inside the wrapped scope
 * are rewritten to `/^ ^/` so the outer block comment stays valid — the inverse of
 * [SlashStarUncommentingStrategy]. Only a single level of nesting is flattened; deeper nesting
 * (which the uncommenter tracks with superscript depth markers) is not yet emitted here.
 */
class StarCommentingStrategy(val flatten: Boolean) : CommentingStrategy {
    override fun comment(tracker: ModificationTracker, tokens: Sequence<LeafToken>) {
        val document = tracker.document
        val start = tracker.start
        val end = tracker.end
        val offset = start + document.countLeadingWhitespace(start, end)

        if (canUseLineComment(document, start, end)) {
            tracker.insert(offset, "//")
            return
        }

        tracker.insert(offset, "/*")
        tracker.insert(end, "*/")
        if (!flatten) return

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
        val Java: StarCommentingStrategy = StarCommentingStrategy(flatten = true)

        /** Kotlin-style commenting; nested block comments are preserved as-is. */
        val Kotlin: StarCommentingStrategy = StarCommentingStrategy(flatten = false)
    }
}

/**
 * A line comment is usable only when the scope is a single content line that reaches the end of its
 * line, so that a single `//` prefix comments exactly the scope without swallowing following code.
 */
private fun canUseLineComment(document: Document, start: Int, end: Int): Boolean =
    !document.spansMultipleContentLines(start, end) && !document.hasLineContentAfter(start, end)

private fun Document.countLeadingWhitespace(start: Int, end: Int): Int {
    var index = start
    while (index < end && this[index].isWhitespace()) index++
    return index - start
}

private fun Document.spansMultipleContentLines(start: Int, end: Int): Boolean {
    var contentBefore = false
    var newlineAfterContent = false
    for (index in start until end) when (val char = this[index]) {
        '\n' -> if (contentBefore) newlineAfterContent = true
        else -> if (!char.isWhitespace()) {
            if (newlineAfterContent) return true
            contentBefore = true
        }
    }
    return false
}

/**
 * Whether content follows the scope on the same physical line. A scope whose last character is a
 * newline already ends its line, so nothing can follow it there. Otherwise the scan spills past the
 * window into context, stopping at the next newline or EOF.
 */
private fun Document.hasLineContentAfter(start: Int, end: Int): Boolean {
    if (end > start && this[end - 1] == '\n') return false
    var index = end
    while (true) {
        val char = charOrNewline(index)
        when {
            char == '\n' -> return false
            !char.isWhitespace() -> return true
        }
        index++
    }
}

/** Reads a character, treating any out-of-bounds index (EOF) as a line break. */
private fun Document.charOrNewline(index: Int): Char =
    try { this[index] } catch (_: IndexOutOfBoundsException) { '\n' }
