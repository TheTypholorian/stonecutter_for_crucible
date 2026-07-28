package dev.kikugie.stitcher.util

internal val LINE_BREAKS: CharArray = charArrayOf('\r', '\n')
internal val WORD_BREAKS: CharArray = charArrayOf(' ', '\t')
internal val WHITESPACES: CharArray = WORD_BREAKS + LINE_BREAKS

internal fun String.titlecase(): String = replaceFirstChar {
    if (it.isLowerCase()) it.titlecase() else it.toString()
}

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
