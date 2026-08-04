package dev.kikugie.stitcher.util

internal inline fun <T> List<T>.indexOfFirst(startIndex: Int, predicate: (T) -> Boolean): Int {
    for (i in startIndex until size) if (predicate(get(i))) return i
    return -1
}

internal fun <T> MutableList<T>.append(list: List<T>, startIndex: Int, endIndex: Int): Int {
    for (i in startIndex until endIndex) this += list[i]
    return (endIndex - startIndex).coerceAtLeast(0)
}
