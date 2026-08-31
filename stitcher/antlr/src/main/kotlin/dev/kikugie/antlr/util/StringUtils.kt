package dev.kikugie.antlr.util

import java.util.*

public fun String.toCamelCase(capFirstChar: Boolean = false, locale: Locale = Locale.getDefault(), vararg delimiters: Char): String {
    if (isEmpty() || delimiters.isEmpty()) return this

    val builder = StringBuilder(length)
    var capitalizeNext = capFirstChar
    for (char in lowercase(locale)) when (char) {
        in delimiters -> capitalizeNext = true
        else if (!capitalizeNext) -> builder.append(char)
        else -> {
            builder.append(char.uppercase(locale))
            capitalizeNext = false
        }
    }
    return builder.toString()
}
