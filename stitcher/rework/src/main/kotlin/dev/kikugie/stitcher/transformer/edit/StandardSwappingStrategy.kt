package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.document.minIndex
import dev.kikugie.stitcher.util.LINE_BREAKS
import dev.kikugie.stitcher.util.WORD_BREAKS

/**
 * Default swap substitution strategy.
 *
 * The substituted [text] is re-indented to match the line the substitution begins on, resolved from
 * the surrounding document context (not from the replaced body). Leading and trailing blank lines of
 * the original scope are preserved. When the substitution point sits mid-line — e.g. right after a
 * `/*$ id $*/` opener — the first inserted line is left un-indented while continuation lines take the
 * opener line's indentation.
 */
object StandardSwappingStrategy : SwappingStrategy {
    override fun substitute(tracker: ModificationTracker, text: String) {
        val document = tracker.document
        val scope = document.subSequence(tracker.start, tracker.end).toString()

        val lead = scope.countOffset()
        val trail = scope.reversed().countOffset()
        val coreStart = tracker.start + lead
        val coreEnd = tracker.end - trail

        val indent = document.lineIndent(coreStart)
        val midLine = document.hasLineContentBefore(coreStart)
        tracker.replace(coreStart, coreEnd, text.reindent(indent, dropFirstIndent = midLine))
    }
}

/** Counts the character offset of leading blank lines, mirroring the legacy swap strategy. */
private fun CharSequence.countOffset(): Int {
    var count = 0
    var seenNewLine = false
    for (char in this) when (char) {
        in WORD_BREAKS -> if (seenNewLine) break else count++
        in LINE_BREAKS -> { count++; seenNewLine = true }
        else -> break
    }
    return if (seenNewLine) count else 0
}

private fun String.reindent(indent: String, dropFirstIndent: Boolean): String {
    val reindented = replaceIndent(indent)
    return if (dropFirstIndent && reindented.startsWith(indent)) reindented.removePrefix(indent) else reindented
}

/** The character index of the start of the line containing [index], reading into context if needed. */
private fun Document.lineStart(index: Int): Int {
    val min = minIndex()
    var start = index
    while (start > min && charOrNewline(start - 1) != '\n') start--
    return start
}

/** The leading whitespace (spaces and tabs) of the line containing [index]. */
private fun Document.lineIndent(index: Int): String = buildString {
    var cursor = lineStart(index)
    while (true) {
        val char = charOrNewline(cursor)
        if (char == ' ' || char == '\t') append(char) else break
        cursor++
    }
}

/** Whether the line containing [index] has any non-whitespace content before [index]. */
private fun Document.hasLineContentBefore(index: Int): Boolean {
    var cursor = lineStart(index)
    while (cursor < index) {
        if (!charOrNewline(cursor).isWhitespace()) return true
        cursor++
    }
    return false
}

private fun Document.charOrNewline(index: Int): Char =
    try { this[index] } catch (_: IndexOutOfBoundsException) { '\n' }
