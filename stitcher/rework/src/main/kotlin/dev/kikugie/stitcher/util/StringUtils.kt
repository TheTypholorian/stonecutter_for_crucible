package dev.kikugie.stitcher.util

internal val LINE_BREAKS: CharArray = charArrayOf('\r', '\n')
internal val WORD_BREAKS: CharArray = charArrayOf(' ', '\t')
internal val WHITESPACES: CharArray = WORD_BREAKS + LINE_BREAKS

internal fun String.titlecase(): String = replaceFirstChar {
    if (it.isLowerCase()) it.titlecase() else it.toString()
}
