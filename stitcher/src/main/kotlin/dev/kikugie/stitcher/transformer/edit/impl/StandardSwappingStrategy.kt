package dev.kikugie.stitcher.transformer.edit.impl

import dev.kikugie.commons.text.reverseView
import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.transformer.edit.SwappingStrategy
import dev.kikugie.stitcher.transformer.edit.hasLineContentBefore
import dev.kikugie.stitcher.transformer.edit.lineIndent
import dev.kikugie.stitcher.util.LINE_BREAKS
import dev.kikugie.stitcher.util.WORD_BREAKS
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry

object StandardSwappingStrategy : SwappingStrategy {
    override fun substitute(config: ConfigurationRegistry, tracker: ModificationTracker, text: String): Unit = with(tracker) {
        val document = document
        val scope = document.subSequence(start, end).toString()

        val lead = scope.countOffset()
        val trail = scope.reverseView().countOffset()
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

