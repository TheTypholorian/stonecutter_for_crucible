package dev.kikugie.stitcher.util

import dev.kikugie.antlr.util.endIndex
import dev.kikugie.antlr.util.toSequence
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.SwapTemplateLexer
import dev.kikugie.stitcher.antlr.charStream

internal val LINE_BREAKS: CharArray = charArrayOf('\r', '\n')
internal val WORD_BREAKS: CharArray = charArrayOf(' ', '\t')
internal val WHITESPACES: CharArray = WORD_BREAKS + LINE_BREAKS

private val QUOTED_ESCAPE: Regex = Regex("""\\(['\\])""")

internal fun String.unescapeQuoted(): String =
    QUOTED_ESCAPE.replace(this) { it.groupValues[1] }

internal fun String.findWordSplit(): Int {
    var index = indexOfFirst { it !in WHITESPACES }
    if (index < 0) return -1

    index = indexOfAny(WHITESPACES, index)
    return if (index < 0) length else index
}

internal fun String.findLookupSplit(match: String, capturing: Boolean): Int {
    val index = indexOf(match)
    return if (index < 0 || !capturing) index else index + match.length
}

internal tailrec fun String.findLineSplit(immediate: Boolean, start: Int = 0): Int {
    val index = indexOfAny(LINE_BREAKS, start)
    if (index < 0 || immediate) return index

    return if ((start until index).any { get(it) !in WHITESPACES }) index
    else findLineSplit(false, index + 1)
}

internal fun CharSequence.withoutTrailingBlankLine(): CharSequence {
    var count = 0
    var seenNewLine = false
    for (i in lastIndex downTo 0) when (val ch = get(i)) {
        in WORD_BREAKS -> if (seenNewLine) break else count++
        in LINE_BREAKS -> {
            count++; seenNewLine = true
        }

        else -> break
    }
    if (!seenNewLine) count = 0
    return if (count == 0) this else dropLast(count)
}

internal fun String.findInsertionPlaces(): Sequence<InsertionPlace> =
    SwapTemplateLexer(charStream()).toSequence().map(::InsertionPlace).filter { it.number > 0 }

@JvmInline
internal value class InsertionPlace(val token: AntlrToken) {
    val start: Int inline get() = token.startIndex
    val end: Int inline get() = token.endIndex

    val number: Int
        get() = token.text.trim('$', '{', '}').toIntOrNull() ?: 0
}
