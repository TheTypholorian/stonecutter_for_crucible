@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.util

internal fun <T> firstNotNull(vararg arguments: T?): T = arguments.first { it != null }!!
internal fun <T> lastNotNull(vararg arguments: T?): T = arguments.last { it != null }!!
