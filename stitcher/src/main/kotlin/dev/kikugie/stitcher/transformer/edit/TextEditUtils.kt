package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.maxIndex
import dev.kikugie.stitcher.document.minIndex

/** The character at [index], or `'\n'` when [index] falls outside the document. */
internal fun Document.charOrNewline(index: Int): Char = try {
    this[index]
} catch (_: IndexOutOfBoundsException) {
    '\n'
}

/** The character index at which the line containing [index] starts, reading into context if needed. */
internal fun Document.lineStart(index: Int): Int {
    val min = minIndex()
    var start = index
    while (start > min && charOrNewline(start - 1) != '\n') start--
    return start
}

/** The leading whitespace (spaces and tabs) of the line containing [index]. */
internal fun Document.lineIndent(index: Int): String = buildString {
    var cursor = lineStart(index)
    while (true) {
        val char = charOrNewline(cursor)
        if (char == ' ' || char == '\t') append(char) else break
        cursor++
    }
}

/**
 * Offset from [start] of the first non-whitespace character before [end].
 *
 * Adding this to [start] lands an inserted comment opener after the line's
 * indent instead of before it, which keeps the disabled text aligned with its neighbours.
 */
internal fun Document.countLeadingIndent(start: Int, end: Int): Int {
    for (index in start until end) when (get(index)) {
        ' ', '\t', '\n' -> continue
        else -> return index - start
    }
    return end - start
}

/** Whether the trimmed content between [start] and [end] covers more than one line. */
internal fun Document.spansMultipleLines(start: Int, end: Int): Boolean {
    val content: CharSequence = subSequence(start, end).trim()
    return '\n' in content
}

/** Whether the line containing [index] has any non-whitespace content before [index]. */
internal fun Document.hasLineContentBefore(index: Int): Boolean {
    var cursor = lineStart(index)
    while (cursor < index) {
        if (!charOrNewline(cursor).isWhitespace()) return true
        cursor++
    }
    return false
}

/**
 * Whether any non-whitespace content follows the range between [start] and [end] on its last line.
 *
 * A line comment would swallow that content, so a format with both comment shapes has to fall back
 * to a block comment when this is `true`.
 */
internal fun Document.hasLineContentAfter(start: Int, end: Int): Boolean {
    var last: Int = end - 1
    while (last >= start && get(last).isWhitespace()) last--

    for (index in last + 1 until maxIndex()) when (get(index)) {
        ' ', '\t' -> continue
        '\n' -> break
        else -> return true
    }
    return false
}
