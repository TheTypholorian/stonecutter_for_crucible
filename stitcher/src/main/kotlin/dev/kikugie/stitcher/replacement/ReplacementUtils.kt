package dev.kikugie.stitcher.replacement

import dev.kikugie.stonecutter.model.process.Replacement
import java.util.EnumSet
import java.util.regex.MatchResult
import kotlin.collections.contains
import kotlin.enums.enumEntries

context(exec: ReplacementExecutor)
internal val Replacement.isActive: Boolean
    get() = identifier == null || identifier in exec.identifiers

internal fun MatchResult.nextIndex(text: CharSequence): Int {
    val next = end() + if (end() == start()) 1 else 0
    return if (next <= text.length) next else -1
}

inline fun <reified T : Enum<T>> Int.toEnumSet(): EnumSet<T> = EnumSet.noneOf(T::class.java).apply {
    if (this@toEnumSet == 0) return@apply

    val entries = enumEntries<T>()
    for ((i, entry) in entries.withIndex())
        if (this@toEnumSet ushr i and 1 != 0) this += entry
}

inline fun <reified T : Enum<T>> EnumSet<T>.toInt(): Int {
    var value = 0
    val entries = enumEntries<T>()
    check(entries.size <= 64) { "Can't encode ${T::class.qualifiedName} as Long" }

    for (entry in entries) {
        if (entry in this) value += 1
        value = value shl 1
    }
    return value
}
