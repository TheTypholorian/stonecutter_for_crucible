@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stonecutter.util

import dev.kikugie.stonecutter.VersionString
import dev.kikugie.stonecutter.VersionString.Companion.STONECUTTER_KNOWN
import dev.kikugie.stonecutter.VersionString.Companion.SUPPORTS_WILDCARD
import dev.kikugie.stonecutter.VersionStringMeta
import dev.kikugie.stonecutter.controller.flag.StonecutterFlag
import dev.kikugie.stonecutter.data.ParsedVersion

internal const val IDENTIFIER_PATTERN: String = "[_a-zA-Z][_\\-a-zA-Z0-9]*"

private val IDENTIFIER_REGEX: Regex = Regex(IDENTIFIER_PATTERN)

/**
 * Checks if the [version] lies within this range, allowing version literals as the bounds:
 * ```kotlin
 * if (sc.current.parsed in "1.20".."1.22") { /* ... */ }
 * ```
 * @throws IllegalArgumentException if either bound can't be read as a single version
 */
@VersionStringMeta(include = [SUPPORTS_WILDCARD, STONECUTTER_KNOWN])
public inline operator fun ClosedRange<@VersionString String>.contains(version: ParsedVersion): Boolean =
    version within this

/**
 * Checks if the [version] lies within this range, allowing version literals as the bounds:
 * ```kotlin
 * if (sc.current.parsed in "1.20"..<"1.22") { /* ... */ }
 * ```
 * @throws IllegalArgumentException if either bound can't be read as a single version
 */
@VersionStringMeta(include = [SUPPORTS_WILDCARD, STONECUTTER_KNOWN])
public inline operator fun OpenEndRange<@VersionString String>.contains(version: ParsedVersion): Boolean =
    version within this

internal fun stonecutterProperty(key: String): String =
    "dev.kikugie.stonecutter.$key"

internal val StonecutterFlag<*>.propertyKey: String
    inline get() = stonecutterProperty(key)

internal fun String.isValidIdentifier(): Boolean = when (this) {
    "if", "else", "elif" -> false
    else -> matches(IDENTIFIER_REGEX)
}
