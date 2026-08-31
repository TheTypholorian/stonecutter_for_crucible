package dev.kikugie.stitcher.transformer.edit.impl

import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.document.minIndex
import dev.kikugie.stitcher.transformer.edit.SwappingStrategy
import dev.kikugie.stitcher.util.LINE_BREAKS
import dev.kikugie.stitcher.util.WORD_BREAKS

object StandardSwappingStrategy : SwappingStrategy {
    override fun substitute(tracker: ModificationTracker, text: String): Unit = with(tracker) {
        val document = document
        val scope = document.subSequence(start, end).toString()

        val lead = scope.countOffset()
        val trail = scope.reversed().countOffset()
        val coreStart = start + lead
        val coreEnd = end - trail

        val indent = document.lineIndent(coreStart)
        val midLine = document.hasLineContentBefore(coreStart)
        replace(coreStart, coreEnd, text.reindent(indent, dropFirstIndent = midLine))
    }

    private fun readResolve(): Any = this
}

/** Counts the character offset of leading blank lines, mirroring the legacy swap strategy. */
private fun CharSequence.countOffset(): Int {
    var count = 0
    var seenNewLine = false
    for (char in this) when (char) {
        in WORD_BREAKS -> if (seenNewLine) break else count++
        in LINE_BREAKS -> {
            count++; seenNewLine = true
        }

        else -> break
    }
    return if (seenNewLine) count else 0
}

private fun String.reindent(indent: String, dropFirstIndent: Boolean): String {
    if (!dropFirstIndent) return replaceIndent(indent)
    // Mid-line substitution: the first line is inline and keeps no indentation, so it must be
    // excluded from the common-indent computation — otherwise its zero indent would anchor the
    // continuation lines to their own relative indent instead of the scope's.
    val newline = indexOf('\n')
    if (newline < 0) return this
    return substring(0, newline) + '\n' + substring(newline + 1).replaceIndent(indent)
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

private fun Document.charOrNewline(index: Int): Char = try {
    this[index]
} catch (_: IndexOutOfBoundsException) {
    '\n'
}
