package dev.kikugie.stitcher.util

internal fun <T> MutableList<T>.append(list: List<T>, startIndex: Int, endIndex: Int): Int {
    for (i in startIndex until endIndex) this += list[i]
    return (endIndex - startIndex).coerceAtLeast(0)
}
