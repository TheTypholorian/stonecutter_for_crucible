@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stonecutter.util

import dev.kikugie.commons.then
import dev.kikugie.stonecutter.controller.flag.StonecutterFlag
import dev.kikugie.stonecutter.data.ParsedVersion

internal const val IDENTIFIER_PATTERN: String = "[_a-zA-Z][_\\-a-zA-Z0-9]*"

private val IDENTIFIER_REGEX: Regex = Regex(IDENTIFIER_PATTERN)

private val ASCII_ESCAPES: List<String> = buildList(128) {
    // `toString(16)` is used over interpolation, which would render the index in base 10.
    for (i in 0x00..0x1f) add("\\u" + i.toString(16).padStart(4, '0'))
    for (i in 0x20..0x7f) add(i.toChar().toString())
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

/**
 * Checks if the [version] lies within this range, allowing version literals as the bounds:
 * ```kotlin
 * if (sc.current.parsed in "1.20".."1.22") { /* ... */ }
 * ```
 * @throws IllegalArgumentException if either bound can't be read as a single version
 */
public inline operator fun ClosedRange<String>.contains(version: ParsedVersion): Boolean =
    version within this

/**
 * Checks if the [version] lies within this range, allowing version literals as the bounds:
 * ```kotlin
 * if (sc.current.parsed in "1.20"..<"1.22") { /* ... */ }
 * ```
 * @throws IllegalArgumentException if either bound can't be read as a single version
 */
public inline operator fun OpenEndRange<String>.contains(version: ParsedVersion): Boolean =
    version within this


/**
 * Converts the character to its escaped representation based on the input flag.
 *
 * @param multiline Indicates whether to use multiline-friendly escaping. If true,
 * certain characters like newline (`\n`) and carriage return (`\r`) are converted
 * to their literal forms instead of escape sequences.
 */
public fun Char.escape(multiline: Boolean = false): String = when (this) {
    '\\' -> "\\\\"
    '\t' -> if (multiline) toString() else "\\t"
    '\n' -> if (multiline) toString() else "\\n"
    '\r' -> if (multiline) toString() else "\\r"
    else -> if (code >= 128) toString() else ASCII_ESCAPES[code]
}

/**
 * Escapes the characters in the string based on the input flag.
 *
 * @param multiline Specifies whether multiline-friendly escaping should be applied.
 * If true, certain characters like newline (`\n`) and carriage return (`\r`) are kept
 * in their literal forms instead of being escaped.
 */
public fun String.escape(multiline: Boolean = false): String = buildString {
    for (it in this@escape) append(it.escape(multiline))
}

/**
 * Returns a new string with escape sequences in the original string replaced by their corresponding characters.
 *
 * Common escape sequences such as `\n`, `\t`, `\b`, and Unicode escapes (e.g., `\u1234`) are processed.
 * Escape sequences that are not recognised are retained in their original form.
 */
public fun String.unescape(): String {
    if (isBlank()) return this

    val builder = StringBuilder(length)
    var index = 0
    while (index < length) {
        val char = get(index)

        if (char != '\\' || index == length - 1) {
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
    'u' -> unicode(input, index, digits = 4)
    'U' -> unicode(input, index, digits = 8)
    else -> append("\\$it") then 2
}

/**
 * Appends the code point of the `\u`/`\U` escape starting at [index] and spanning [digits] hex digits.
 * Truncated, non-hexadecimal and out-of-range escapes are appended as-is.
 * @return the number of consumed characters
 */
private fun StringBuilder.unicode(input: String, index: Int, digits: Int): Int {
    val consumed = digits + 2
    val end = index + consumed
    val code = if (end > input.length) null else input.substring(index + 2, end).toIntOrNull(16)
    return if (code == null || !Character.isValidCodePoint(code)) append(input, index, index + 2) then 2
    else appendCodePoint(code) then consumed
}

internal fun stonecutterProperty(key: String): String =
    "dev.kikugie.stonecutter.$key"

internal val StonecutterFlag<*>.propertyKey: String
    inline get() = stonecutterProperty(key)

internal fun String.isValidIdentifier(): Boolean = when (this) {
    "if", "else", "elif" -> false
    else -> matches(IDENTIFIER_REGEX)
}
