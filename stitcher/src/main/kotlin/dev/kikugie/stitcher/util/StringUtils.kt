package dev.kikugie.stitcher.util

import dev.kikugie.antlr.util.endIndex
import dev.kikugie.antlr.util.toSequence
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.SwapTemplateLexer
import dev.kikugie.stitcher.antlr.charStream
import kotlin.math.min

internal val LINE_BREAKS: CharArray = charArrayOf('\r', '\n')
internal val WORD_BREAKS: CharArray = charArrayOf(' ', '\t')
internal val WHITESPACES: CharArray = WORD_BREAKS + LINE_BREAKS

private val QUOTED_ESCAPE: Regex = Regex("""\\(['\\])""")

internal fun String.titlecase(): String = replaceFirstChar {
    if (it.isLowerCase()) it.titlecase() else it.toString()
}

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

internal fun String.findClosest(samples: Iterable<String>, threshold: Int = Int.MAX_VALUE): String? {
    var distance: Int = threshold
    var candidate: String? = null
    for (it in samples) {
        val d = this distanceTo it
        if (d > distance) continue
        distance = d
        candidate = it
    }
    return candidate
}

// https://en.wikipedia.org/wiki/Damerau%E2%80%93Levenshtein_distance
internal infix fun String.distanceTo(target: String): Int {
    val sourceLength = length
    val targetLength = target.length
    if (sourceLength == 0) return targetLength
    if (targetLength == 0) return sourceLength

    val distances: Array<IntArray> = Array(sourceLength + 1) { IntArray(targetLength + 1) }
    for (i in 0..<sourceLength) distances[i][0] = i
    for (j in 0..<targetLength) distances[0][j] = j
    for (i in 1..<sourceLength + 1) for (j in 1..<targetLength + 1) {
        val cost = if (this[i - 1] == target[j - 1]) 0 else 1
        distances[i][j] = min(min(distances[i - 1][j] + 1, distances[i][j - 1] + 1), distances[i - 1][j - 1] + cost)
        if (i > 1 && j > 1 && this[i - 1] == target[j - 2] && this[i - 2] == target[j - 1])
            distances[i][j] = min(distances[i][j], distances[i - 2][j - 2] + cost)
    }
    return distances[sourceLength][targetLength]
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

internal fun CharSequence.commonIndent(ignoreFirstLine: Boolean): String {
    var lines: Sequence<String> = splitToSequence('\n')
    if (ignoreFirstLine) lines = lines.drop(1)

    return lines.filter(String::isNotBlank).minOfOrNull { it.takeWhile(Char::isWhitespace) } ?: ""
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
