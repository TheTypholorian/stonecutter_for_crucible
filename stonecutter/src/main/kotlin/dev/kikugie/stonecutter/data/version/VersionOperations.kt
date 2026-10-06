package dev.kikugie.stonecutter.data.version

import dev.kikugie.stonecutter.VersionString.Companion.STONECUTTER_KNOWN
import dev.kikugie.commons.result.inherit
import dev.kikugie.commons.result.mapResult
import dev.kikugie.commons.takeAs
import dev.kikugie.commons.text.times
import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.semver.data.Version
import dev.kikugie.semver.data.VersionPredicate
import dev.kikugie.semver.impl.VersionParsingException
import dev.kikugie.stonecutter.AnyVersion
import dev.kikugie.stonecutter.VersionString
import dev.kikugie.stonecutter.VersionString.Companion.SEMVER_ONLY
import dev.kikugie.stonecutter.VersionStringMeta
import dev.kikugie.stonecutter.VersionString.Companion.SUPPORTS_OPERATORS
import dev.kikugie.stonecutter.VersionString.Companion.SUPPORTS_WILDCARD
import dev.kikugie.stonecutter.data.ParsedVersion
import java.util.concurrent.ConcurrentHashMap

/**Marker interface for extensions supporting version-based conditional evaluation.*/
public interface VersionOperations<T : Version> {
    /**
     * Parses the provided [version] string as the [T] version.
     * @throws IllegalArgumentException if parsing fails, with [VersionParsingException] as its cause
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#version-types">Version specification</a>
     */
    public fun parse(version: @VersionString AnyVersion): T

    /**
     * Parses the provided [version] string as the [T] version.
     * Allows for the `-*` suffix for [SemanticVersion]s.
     * @throws IllegalArgumentException if parsing fails, with [VersionParsingException] as its cause
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#version-types">Version specification</a>
     */
    @VersionStringMeta(include = [SUPPORTS_WILDCARD])
    public fun parseAsPredicate(version: @VersionString AnyVersion): T

    /**
     * Tries to parse the provided [version] string as the [T] version.
     * @return [Result] with [VersionParsingException] if parsing fails.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#version-types">Version specification</a>
     */
    public fun tryParse(version: @VersionString AnyVersion): Result<T>

    /**
     * Tries to parse the provided [version] string as the [T] version.
     * Allows for the `-*` suffix for [SemanticVersion]s.
     * @return [Result] with [VersionParsingException] if parsing fails.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#version-types">Version specification</a>
     */
    @VersionStringMeta(include = [SUPPORTS_WILDCARD])
    public fun tryParseAsPredicate(version: @VersionString AnyVersion): Result<T>

    /**
     * Checks if the provided [version] satisfies the [predicates].
     *
     * Predicates can be separated with spaces in the same string or provided as separate strings:
     * ```kt
     * val version = parse("1.1")
     * assert(eval(version, ">1.0", "<2.0"))
     * assert(eval(version, ">1.0 <2.0"))
     * ```
     * @throws IllegalArgumentException if parsing fails, with [VersionParsingException] as its cause
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#version-types">Version specification</a>
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#predicate-operators">Predicate specification</a>
     */
    @VersionStringMeta(include = [SUPPORTS_WILDCARD, SUPPORTS_OPERATORS, STONECUTTER_KNOWN])
    public fun eval(version: Version, vararg predicates: @VersionString String): Boolean

    /**
     * Checks if the provided [version] satisfies the [predicates].
     *
     * Predicates can be separated with spaces in the same string or provided as separate strings:
     * ```kt
     * val version = parse("1.1")
     * assert(tryEval(version, ">1.0", "<2.0").getOrThrow())
     * assert(tryEval(version, ">1.0 <2.0").getOrThrow())
     * ```
     * @return [Result] with [VersionParsingException] if parsing fails
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#version-types">Version specification</a>
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#predicate-operators">Predicate specification</a>
     */
    @VersionStringMeta(include = [SUPPORTS_WILDCARD, SUPPORTS_OPERATORS, STONECUTTER_KNOWN])
    public fun tryEval(version: Version, vararg predicates: @VersionString String): Result<Boolean>

    /**
     * Checks if the provided [version] satisfies the [predicates].
     *
     * Predicates can be separated with spaces in the same string or provided as separate strings:
     * ```kt
     * assert(eval("1.1", ">1.0", "<2.0"))
     * assert(eval("1.1", ">1.0 <2.0"))
     * ```
     * @throws IllegalArgumentException if parsing fails, with [VersionParsingException] as its cause
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#version-types">Version specification</a>
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#predicate-operators">Predicate specification</a>
     */
    @VersionStringMeta(include = [STONECUTTER_KNOWN])
    public fun eval(version: @VersionString String, vararg predicates: @VersionString(SUPPORTS_WILDCARD, SUPPORTS_OPERATORS) String): Boolean

    /**
     * Checks if the provided [version] satisfies the [predicates].
     *
     * Predicates can be separated with spaces in the same string or provided as separate strings:
     * ```kt
     * assert(tryEval("1.1", ">1.0", "<2.0").getOrThrow())
     * assert(tryEval("1.1", ">1.0 <2.0").getOrThrow())
     * ```
     * @return [Result] with [VersionParsingException] if parsing fails
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#version-types">Version specification</a>
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#predicate-operators">Predicate specification</a>
     */
    @VersionStringMeta(include = [STONECUTTER_KNOWN])
    public fun tryEval(version: @VersionString String, vararg predicates: @VersionString(SUPPORTS_WILDCARD, SUPPORTS_OPERATORS) String): Result<Boolean>

    /**
     * Parses each version as [T] and compares them
     * according to the [Comparable] specification.
     * @throws IllegalArgumentException if parsing fails, with [VersionParsingException] as its cause
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#version-types">Version specification</a>
     */
    @VersionStringMeta(include = [SUPPORTS_WILDCARD, STONECUTTER_KNOWN])
    public fun compare(version: @VersionString String, other: @VersionString String): Int

    /**
     * Parses each version as [T] and compares them
     * according to the [Comparable] specification.
     * @return [Result] with [VersionParsingException] if parsing fails
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#version-types">Version specification</a>
     */
    @VersionStringMeta(include = [SUPPORTS_WILDCARD, STONECUTTER_KNOWN])
    public fun tryCompare(version: @VersionString String, other: @VersionString String): Result<Int>

    /**
     * Checks if the [version] lies within this range, allowing version literals as the bounds:
     * ```kotlin
     * if (current.parsed in "1.20".."1.22") {
     *     // ...
     * }
     * ```
     * @throws IllegalArgumentException if either bound can't be read as a single version
     */
    @VersionStringMeta(include = [SUPPORTS_WILDCARD, STONECUTTER_KNOWN])
    public operator fun ClosedRange<@VersionString String>.contains(version: ParsedVersion): Boolean =
        version within this

    /**
     * Checks if the [version] lies within this range, allowing version literals as the bounds:
     * ```kotlin
     * if (current.parsed in "1.20"..<"1.22") {
     *     // ...
     * }
     * ```
     * @throws IllegalArgumentException if either bound can't be read as a single version
     */
    @VersionStringMeta(include = [SUPPORTS_WILDCARD, STONECUTTER_KNOWN])
    public operator fun OpenEndRange<@VersionString String>.contains(version: ParsedVersion): Boolean =
        version within this
}

@Suppress("UNCHECKED_CAST")
internal abstract class BasicOperations<T : Version> : VersionOperations<T> {
    protected val versions: MutableMap<String, Version> = ConcurrentHashMap()
    protected val predicates: MutableMap<String, Version> = ConcurrentHashMap()

    override fun parse(version: AnyVersion): T = tryParse(version).getOrElse {
        throw it.takeAs<VersionParsingException>().asIllegalArg(version)
    }

    override fun tryParse(version: AnyVersion): Result<T> = versions.runCatching {
        computeIfAbsent(version) { tryParseVersion(it, false) } as T
    }

    override fun parseAsPredicate(version: AnyVersion): T = tryParseAsPredicate(version).getOrElse {
        throw it.takeAs<VersionParsingException>().asIllegalArg(version)
    }

    override fun tryParseAsPredicate(version: AnyVersion): Result<T> = predicates.runCatching {
        computeIfAbsent(version) { tryParseVersion(it, true) } as T
    }

    override fun eval(version: Version, vararg predicates: String): Boolean = tryEval(version, *predicates).getOrElse {
        val error = it.takeAs<VersionParsingException>()
        throw error.asIllegalArg(predicates.joinToString(), position = predicates.joinedOffset(error.position))
    }

    override fun tryEval(version: Version, vararg predicates: String): Result<Boolean> =
        kotlin.runCatching { unpackPredicates(*predicates).all { it(version) } }

    override fun eval(version: String, vararg predicates: String): Boolean =
        eval(parse(version), *predicates)

    override fun tryEval(version: String, vararg predicates: String): Result<Boolean> =
        tryParse(version).mapResult { tryEval(it as Version, *predicates) }

    override fun compare(version: String, other: String): Int =
        parseAsPredicate(version) compareTo parseAsPredicate(other)

    override fun tryCompare(version: String, other: String): Result<Int> =
        // Is this what functional programming shills dream of?
        tryParseAsPredicate(version).mapResult { a -> tryParseAsPredicate(other).map { b -> a compareTo b } }

    protected abstract fun tryParseVersion(str: AnyVersion, predicate: Boolean): Version
    protected abstract fun unpackPredicates(vararg str: String): List<VersionPredicate>
}

internal object LenientOperations : BasicOperations<Version>() {
    override fun tryParseVersion(str: AnyVersion, predicate: Boolean): Version =
        if (predicate) Version.parseAsPredicate(str) else Version.parse(str)

    override fun unpackPredicates(vararg str: String): List<VersionPredicate> =
        str.unpack(VersionPredicate.Companion)
}

internal object SemanticOperations : BasicOperations<SemanticVersion>() {
    override fun tryParseVersion(str: AnyVersion, predicate: Boolean): Version =
        if (predicate) SemanticVersion.parseAsPredicate(str) else SemanticVersion.parse(str)

    override fun unpackPredicates(vararg str: String): List<VersionPredicate> =
        str.unpack(VersionPredicate.Semantic)
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun VersionParsingException.asIllegalArg(
    str: String,
    message: String = this.message,
    position: Int = this.position
): IllegalArgumentException {
    val pointer = ' ' * position + '^'
    val message = "$message:\n| $str\n| $pointer"
    return IllegalArgumentException(message).apply { addSuppressed(this@asIllegalArg) }
}

private fun Array<out String>.joinedOffset(offset: Int): Int {
    var consumed = 0
    var separators = 0
    for (str in this) when {
        consumed + str.length > offset -> break
        else -> {
            consumed += str.length
            separators++
        }
    }

    return offset + separators * 2
}

internal fun Array<out String>.unpack(ops: VersionPredicate.Operations): List<VersionPredicate> = buildList {
    var length = 0
    for (str in this@unpack) {
        var cursor = 0
        while (cursor < str.length) when (str[cursor]) {
            ' ', '\t' -> cursor++
            else -> cursor = parsePredicate(str, cursor, length, ops, this)
        }
        length += str.length
    }
}

private fun parsePredicate(str: String, pos: Int, len: Int, ops: VersionPredicate.Operations, list: MutableList<VersionPredicate>): Int {
    val end = ops.locate(str, pos)
    if (end < 0) throw VersionParsingException("Not a valid predicate", pos)

    list += ops.parse(str.substring(pos, end)).getOrElse {
        it as VersionParsingException
        throw VersionParsingException(it.message, it.position + pos + len).inherit(it)
    }
    return end
}
