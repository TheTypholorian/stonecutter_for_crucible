package dev.kikugie.stitcher.transformer.edit.impl

import dev.kikugie.stitcher.document.charSequenceContent
import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.document.minIndex
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.transformer.edit.CommentingStrategy
import dev.kikugie.stitcher.util.LINE_BREAKS
import dev.kikugie.stitcher.util.WORD_BREAKS
import dev.kikugie.stitcher.util.commonIndent
import dev.kikugie.stitcher.util.withoutTrailingBlankLine
import kotlin.math.min
import kotlin.text.iterator

class LineCommentingStrategy(val symbol: String) : CommentingStrategy {
    override fun comment(tracker: ModificationTracker, tokens: Sequence<LeafToken>): Unit = with(tracker) {
        // Checks if this scope is following a line comment, or block one
        val isFirstLineInline: Boolean = start > document.minIndex() && document[start - 1] !in LINE_BREAKS
        val contentSequence: CharSequence = tracker.charSequenceContent().withoutTrailingBlankLine()
        val commonIndent: String = contentSequence.commonIndent(isFirstLineInline)

        var offset = start
        for ((i, line) in contentSequence.splitToSequence('\n').withIndex()) when {
            i == 0 && isFirstLineInline -> insert(offset, symbol)
            line.isBlank() -> replace(offset, offset + line.length, "$commonIndent$symbol")
            else -> insert(offset + commonIndent.length, symbol)
        }.also {
            offset += line.length + 1
        }
    }
}
