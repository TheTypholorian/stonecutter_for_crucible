package dev.kikugie.stitcher.replacement

import dev.kikugie.stonecutter.model.process.Replacement
import java.util.*
import kotlin.enums.enumEntries

@Suppress("UNCHECKED_CAST")
internal inline fun <reified T : Replacement> List<ReplacementState<*>>.ofType(): List<ReplacementState<T>> =
    filter { it.value is T } as List<ReplacementState<T>>

inline fun <reified T : Enum<T>> Int.toEnumSet(): EnumSet<T> = EnumSet.noneOf(T::class.java).apply {
    if (this@toEnumSet == 0) return@apply

    val entries = enumEntries<T>()
    for ((i, entry) in entries.withIndex())
        if (this@toEnumSet ushr i and 1 != 0) this += entry
}

inline fun <reified T : Enum<T>> EnumSet<T>.toInt(): Int {
    var value = 0
    val entries = enumEntries<T>()
    check(entries.size <= 32) { "Can't encode ${T::class.qualifiedName} as Int" }

    for (entry in entries) {
        if (entry in this) value += 1
        value = value shl 1
    }
    return value
}
