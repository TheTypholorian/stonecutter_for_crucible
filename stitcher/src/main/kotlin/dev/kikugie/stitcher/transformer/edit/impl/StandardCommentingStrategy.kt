package dev.kikugie.stitcher.transformer.edit.impl

import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.token.type.BuiltinTokenType
import dev.kikugie.stitcher.transformer.edit.CommentingStrategy

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
        val offset = start + document.countLeadingIndent(start, end)

        if (canUseLineComment(document, start, end)) {
            tracker.insert(offset, "//")
            return
        }

        tracker.insert(offset, "/*")
        // A trailing `//` line comment would swallow the closer, so push `*/` onto its own line.
        tracker.insert(end, if (document.lastLineHasLineComment(start, end)) "\n*/" else "*/")
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
 * A line comment is usable only when the scope's content occupies a single physical line that reaches
 * the end of its line, so that a single `//` prefix comments exactly the scope without swallowing
 * following code. Trailing blank lines are ignored — they stay outside the comment. A scope holding a
 * nested Stitcher marker is excluded too: a line comment would hide that marker from the next scan.
 */
private fun canUseLineComment(document: Document, start: Int, end: Int): Boolean =
    !document.spansMultipleLines(start, end) &&
        !document.hasLineContentAfter(start, end) &&
        !document.containsNestedMarker(start, end)

/**
 * Whether the scope holds a nested Stitcher marker — a line- or block-comment opener immediately
 * followed by one of the marker sigils (`?` condition, `$` swap, `~` replacement). Such a marker must
 * stay scannable, so its scope has to be wrapped in a block comment rather than hidden behind a `//`.
 */
private fun Document.containsNestedMarker(start: Int, end: Int): Boolean {
    for (index in start until end - 2) if (this[index] == '/' && (this[index + 1] == '/' || this[index + 1] == '*'))
        when (this[index + 2]) {
            '?', '$', '~' -> return true
            else -> {}
        }
    return false
}

/**
 * The width of the first physical line's indentation. Only spaces and tabs are skipped: a leading
 * newline (a blank line at the top of the scope) belongs inside the block comment, not before it.
 */
private fun Document.countLeadingIndent(start: Int, end: Int): Int {
    var index = start
    while (index < end && (this[index] == ' ' || this[index] == '\t')) index++
    return index - start
}

/**
 * Whether the scope's content spans more than one physical line. Leading and trailing blank lines
 * are disregarded: only a newline sitting between two pieces of non-whitespace content counts.
 */
private fun Document.spansMultipleLines(start: Int, end: Int): Boolean {
    var lastContent = -1
    for (index in start until end) if (!this[index].isWhitespace()) lastContent = index
    if (lastContent < 0) return false
    for (index in start until lastContent) if (this[index] == '\n') return true
    return false
}

/** Whether the last physical line of the scope holds a `//` line comment that would eat a trailing closer. */
private fun Document.lastLineHasLineComment(start: Int, end: Int): Boolean {
    var lineStart = start
    for (index in start until end) if (this[index] == '\n') lineStart = index + 1
    for (index in lineStart until end - 1) if (this[index] == '/' && this[index + 1] == '/')
        return true
    return false
}

/**
 * Whether code follows the scope's content on the same physical line — i.e. a single `//` prefix
 * would swallow it. The scan begins right after the scope's last non-whitespace character (so trailing
 * blank lines are ignored) and spills past the window into context, stopping at the next newline or EOF.
 */
private fun Document.hasLineContentAfter(start: Int, end: Int): Boolean {
    var lastContent = -1
    for (index in start until end) if (!this[index].isWhitespace()) lastContent = index

    var index = if (lastContent < 0) start else lastContent + 1
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
private fun Document.charOrNewline(index: Int): Char = try {
    this[index]
} catch (_: IndexOutOfBoundsException) {
    '\n'
}
