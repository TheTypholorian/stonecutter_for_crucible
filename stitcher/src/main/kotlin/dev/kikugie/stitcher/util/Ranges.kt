@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.util

internal fun <T> firstNotNull(vararg arguments: T?): T = arguments.first { it != null }!!
internal fun <T> lastNotNull(vararg arguments: T?): T = arguments.last { it != null }!!

internal fun <T> firstNotNullOrNull(vararg arguments: T?): T? = arguments.firstOrNull { it != null }
internal fun <T> lastNotNullOrNull(vararg arguments: T?): T? = arguments.lastOrNull { it != null }

internal inline fun <T> MutableCollection<T>.addNotNull(value: T?): Boolean =
    if (value == null) false else add(value)
