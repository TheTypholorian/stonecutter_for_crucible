package dev.kikugie.stitcher.util

import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

internal fun <T> firstNotNull(vararg arguments: T?): T = arguments.first { it != null }!!
internal fun <T> lastNotNull(vararg arguments: T?): T = arguments.last { it != null }!!

internal fun <T> firstNotNullOrNull(vararg arguments: T?): T? = arguments.firstOrNull { it != null }
internal fun <T> lastNotNullOrNull(vararg arguments: T?): T? = arguments.lastOrNull { it != null }

internal fun KProperty0<*>.isLazyInitialized(): Boolean {
    if (this !is Lazy<*>) return true

    val access = isAccessible
    isAccessible = true
    return (getDelegate() as Lazy<*>).isInitialized()
        .also { isAccessible = access }
}

