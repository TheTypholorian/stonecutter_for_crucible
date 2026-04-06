package dev.kikugie.stonecutter.util

import dev.kikugie.commons.text.getOrDefault
import dev.kikugie.commons.then
import kotlinx.serialization.json.JsonPrimitive

private val ASCII_ESCAPES: List<String> = buildList(128) {
    for (i in 0x00..0x0f) {
        add(i, "\\u000$i")
    }
    for (i in 0x10..0x1f) {
        add(i, "\\u00$i")
    }
    for (i in 0x20..0x7f) {
        add(i, i.toChar().toString())
    }
    set('\b'.code, "\\b")
    set('\t'.code, "\\t")
    set('\n'.code, "\\n")
    set(12, "\\f")
    set('\r'.code, "\\r")
    set('\"'.code, "\\\"")
    set('\\'.code, "\\\\")
}

internal fun join(a: String, b: String, separator: String): String =
    if (a.isEmpty()) b else "$a$separator$b"

internal fun join(vararg parts: String, separator: String): String = when (parts.size) {
    0 -> ""
    1 -> parts.first()
    2 -> join(parts[0], parts[1], separator)
    else -> parts.filter(String::isNotEmpty).joinToString(separator)
}

public fun Char.escape(multiline: Boolean = false): String = when(this) {
    '\\' -> "\\\\"
    '\t' -> if (multiline) toString() else "\\t"
    '\n' -> if (multiline) toString() else "\\n"
    '\r' -> if (multiline) toString() else "\\r"
    else -> if (code >= 128) toString() else ASCII_ESCAPES[code]
}

public fun String.escape(multiline: Boolean = false): String = buildString {
    for (it in this@escape) append(it.escape(multiline))
}

public fun String.unescape(): String {
    if (isBlank()) return this

    val builder = StringBuilder(length)
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

internal fun String.toIntAutoRadix(): Int = when (val radix = guessRadix()) {
    10 -> toInt()
    else -> removeRadix().toInt(radix)
}

internal fun String.toIntOrNullAutoRadix(): Int? = when (val radix = guessRadix()) {
    10 -> toIntOrNull()
    else -> removeRadix().toIntOrNull(radix)
}

internal fun String.toLongAutoRadix(): Long = when (val radix = guessRadix()) {
    10 -> toLong()
    else -> removeRadix().toLong(radix)
}

internal fun String.toLongOrNullAutoRadix(): Long? = when (val radix = guessRadix()) {
    10 -> toLongOrNull()
    else -> removeRadix().toLongOrNull(radix)
}

internal fun Int.toRadixString(radix: Int): String = when(radix) {
    2 -> "0b${toString(2)}"
    8 -> "0o${toString(8)}"
    16 -> "0x${toString(16)}"
    10 -> toString()
    else -> throw IllegalArgumentException("Radix $radix can't be encoded into the string")
}

internal fun Long.toRadixString(radix: Int): String = when(radix) {
    2 -> "0b${toString(2)}"
    8 -> "0o${toString(8)}"
    16 -> "0x${toString(16)}"
    10 -> toString()
    else -> throw IllegalArgumentException("Radix $radix can't be encoded into the string")
}

private fun String.guessRadix(): Int {
    if (isBlank()) return 10

    var offset = if (first().let { it == '+' || it == '-' }) 1 else 0
    if (getOrDefault(offset) == '0') offset ++ else return 10
    return when(getOrDefault(offset)) {
        'b', 'B' -> 2
        'o', 'O' -> 8
        'x', 'X' -> 16
        else -> 10
    }
}

private fun String.removeRadix(): String {
    val offset = if (first().let { it == '+' || it == '-' }) 3 else 2
    return get(0) + drop(offset)
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
    'U' -> if (index + 9 >= input.length) append("\\U") then 2
    else append(input.substring(index + 2, index + 10).toInt(16).toChar()) then 6
    else -> append("\\$it") then 2
}