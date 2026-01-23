@file:OptIn(ExperimentalContracts::class)

package dev.kikugie.stitcher.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.text.StringBuilder

const val IDENTIFIER_PATTERN: String = "[_a-zA-Z][_\\-a-zA-Z0-9]*"
private val IDENTIFIER_REGEX: Regex = Regex(IDENTIFIER_PATTERN)

internal val LINE_BREAKS: CharArray = charArrayOf('\r', '\n')
internal val WORD_BREAKS: CharArray = charArrayOf(' ', '\t')
internal val WHITESPACES: CharArray = WORD_BREAKS + LINE_BREAKS

fun String.isValidIdentifier(): Boolean = matches(IDENTIFIER_REGEX)

fun CharSequence.hasLineBreak() = any(LINE_BREAKS::contains)

internal inline fun buildString(content: String, action: StringBuilder.() -> Unit): String {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return StringBuilder(content).apply(action).toString()
}