@file:OptIn(ExperimentalContracts::class)
@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.util

import java.util.regex.Pattern
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

const val IDENTIFIER_PATTERN: String = "[_a-zA-Z][_\\-a-zA-Z0-9]*"
private val IDENTIFIER_REGEX: Regex = Regex(IDENTIFIER_PATTERN)

internal val LINE_BREAKS: CharArray = charArrayOf('\r', '\n')
internal val WORD_BREAKS: CharArray = charArrayOf(' ', '\t')
internal val WHITESPACES: CharArray = WORD_BREAKS + LINE_BREAKS

fun String.isValidIdentifier(): Boolean = when(this) {
    "if", "else", "elif" -> false
    else -> matches(IDENTIFIER_REGEX)
}

fun CharSequence.hasLineBreak() = any(LINE_BREAKS::contains)

internal inline fun buildString(content: String, action: StringBuilder.() -> Unit): String {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return StringBuilder(content).apply(action).toString()
}

fun Regex(pattern: String, flags: Int): Regex =
    Pattern.compile(pattern, flags).toRegex()

internal fun String.titlecase(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

internal inline fun String.substring(range: OpenEndRange<Int>): String =
    substring(range.start, range.endExclusive)
