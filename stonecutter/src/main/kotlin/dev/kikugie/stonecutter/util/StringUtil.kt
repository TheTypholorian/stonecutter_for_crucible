package dev.kikugie.stonecutter.util

import dev.kikugie.commons.then
import kotlinx.serialization.json.JsonPrimitive

internal fun join(a: String, b: String, separator: String): String =
    if (a.isEmpty()) b else "$a$separator$b"

internal fun join(vararg parts: String, separator: String): String = when (parts.size) {
    0 -> ""
    1 -> parts.first()
    2 -> join(parts[0], parts[1], separator)
    else -> parts.filter(String::isNotEmpty).joinToString(separator)
}

public fun JsonPrimitive.unwrap(): String =
    if (isString) content.unquote() else content

private fun String.unquote(): String {
    if (length < 2 || first() != '"' || last() != '"') return this

    val builder = StringBuilder(length - 2)
    var index = 1
    while (index < length - 1) {
        val char = get(index)

        if (char != '\\' || index == length - 2) {
            builder.append(char)
            index++
            continue
        }

        index += builder.next(this, index, get(index + 1))
    }
    return builder.toString()
}

private fun StringBuilder.next(input: String, index: Int, it: Char): Int = when (it) {
    '"', '\\', '/' -> append(it) then 2
    'b' -> append('\b') then 2
    'n' -> append('\n') then 2
    'r' -> append('\r') then 2
    't' -> append('\t') then 2
    'f' -> append('\u000c') then 2
    'u' -> if (index + 5 >= input.length) append("\\u") then 2
    else append(input.substring(index + 2, index + 6).toInt(16).toChar()) then 6
    else -> append("\\$it") then 2
}