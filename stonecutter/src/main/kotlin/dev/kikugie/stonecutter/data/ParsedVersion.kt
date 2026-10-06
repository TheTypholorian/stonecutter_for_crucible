package dev.kikugie.stonecutter.data

import dev.kikugie.stonecutter.VersionString
import dev.kikugie.stonecutter.VersionString.Companion.SUPPORTS_OPERATORS
import dev.kikugie.stonecutter.VersionString.Companion.SUPPORTS_WILDCARD
import dev.kikugie.commons.result.invalidArg
import dev.kikugie.commons.takeAs
import dev.kikugie.semver.data.*
import dev.kikugie.semver.impl.VersionParsingException
import dev.kikugie.stonecutter.AnyVersion
import dev.kikugie.stonecutter.data.version.LenientOperations
import dev.kikugie.stonecutter.data.version.asIllegalArg
import dev.kikugie.stonecutter.data.version.unpack
import org.gradle.api.provider.Provider
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * Parsed representation of [StonecutterProject.version], providing additional comparison utilities.
 *
 * This type provides convenience by interpreting the input in favour of the user;
 * however, this may be a disadvantage when precise type checks are required.
 *
 * An operand may be another [ParsedVersion], a [Version], any [CharSequence] (including a Groovy
 * `GString`), a [Number], a [StonecutterProject], or any of those inside a [Provider], [Optional],
 * [Result] or [Lazy]. Text carrying a predicate operator is evaluated as a predicate by [equals]
 * and reduced to its bare version by [compareTo].
 *
 * @property value The underlying parsed version
 * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#version-types">Version Types Documentation</a>
 */
public class ParsedVersion(public val value: Version) : Comparable<Any> {
    /**Creates a parsed version from a string [value]. Throws if the version is invalid.*/
    public constructor(value: @VersionString AnyVersion) : this(LenientOperations.parse(value))

    private val hashCode: Int = computeHashCode(value)

    /**
     * Compares this version to the [other].
     *
     * Accepts any operand this type can interpret as a version. A predicate operator in the
     * operand is discarded, so `this > ">=1.20"` orders against `1.20`.
     *
     * @throws IllegalArgumentException if [other] can't be read as a single version
     */
    override fun compareTo(other: Any): Int = when (val target = VersionTarget.interpret(other)) {
        is VersionTarget.Exact -> value compareTo target.version
        is VersionTarget.Match -> value compareTo target.only(other)
    }

    /**Hash code consistent with [equals], so versions of equal precedence hash alike.*/
    override fun hashCode(): Int = hashCode

    /**Returns the original version string.*/
    override fun toString(): String = value.toString()

    /**
     * Checks if this version is equal to the [other], ignoring SemVer build metadata.
     *
     * Accepts the same operands as [compareTo], and evaluates predicate text instead of
     * comparing against it, so `this == ">=1.20"` reports whether this version satisfies
     * `>=1.20`. An operand that can't be read as a version is unequal rather than an error.
     */
    override fun equals(other: Any?): Boolean = when (val target = VersionTarget.interpretOrNull(other)) {
        null -> false
        is VersionTarget.Exact -> (value compareTo target.version) == 0
        is VersionTarget.Match -> target.predicates.all { it(value) }
    }

    /**
     * Checks if this version is equal to the [other], standing in for the `==` operator.
     *
     * Kotlin rejects `==` between two unrelated final types, so `parsed == "1.0"` doesn't
     * compile even though [equals] accepts a [String] perfectly well.
     * This form accepts every operand [equals] does, predicate text included:
     * ```kotlin
     * if (sc.current.parsed eq "1.0") { /* ... */ }
     * ```
     */
    public infix fun eq(other: Any?): Boolean = this == other

    /**
     * Checks if this version matches the space-separated [predicates].
     * @throws IllegalArgumentException if the predicates can't be parsed
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#predicate-operators">Predicate Operators Documentation</a>
     */
    public infix fun matches(predicates: @VersionString(SUPPORTS_WILDCARD + SUPPORTS_OPERATORS) String): Boolean =
        LenientOperations.eval(value, predicates)

    /**
     * Checks if this version matches every one of the [predicates],
     * each of which may itself hold several space-separated entries.
     * @throws IllegalArgumentException if the predicates can't be parsed
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#predicate-operators">Predicate Operators Documentation</a>
     */
    public fun matches(vararg predicates: @VersionString(SUPPORTS_WILDCARD + SUPPORTS_OPERATORS) String): Boolean =
        LenientOperations.eval(value, *predicates)

    /**
     * Checks if this version matches every one of the [predicates], as a shorthand for [matches]:
     * ```kotlin
     * if (sc.current.parsed(">1.20", "<1.22")) { /* ... */ }
     * ```
     * @throws IllegalArgumentException if the predicates can't be parsed
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#predicate-operators">Predicate Operators Documentation</a>
     */
    public operator fun invoke(vararg predicates: @VersionString(SUPPORTS_WILDCARD + SUPPORTS_OPERATORS) String): Boolean =
        LenientOperations.eval(value, *predicates)

    /**
     * Creates a range from this version to the [other], which is interpreted as in [compareTo].
     * @throws IllegalArgumentException if [other] can't be read as a single version
     */
    public operator fun rangeTo(other: Any): ClosedRange<ParsedVersion> =
        ClosedVersionRange(this, ParsedVersion(other))

    /**
     * Creates a range from this version up to, but excluding, the [other],
     * which is interpreted as in [compareTo].
     * @throws IllegalArgumentException if [other] can't be read as a single version
     */
    public operator fun rangeUntil(other: Any): OpenEndRange<ParsedVersion> =
        OpenVersionRange(this, ParsedVersion(other))

    /**
     * Checks if this version lies within the [range], both of whose bounds are
     * included and interpreted as in [compareTo].
     *
     * Provided for call sites that can't bring the `in` operator into scope, such as Groovy build scripts.
     * @throws IllegalArgumentException if either bound can't be read as a single version
     */
    public infix fun within(range: ClosedRange<*>): Boolean =
        this >= ParsedVersion(range.start) && this <= ParsedVersion(range.endInclusive)

    /**
     * Checks if this version lies within the [range], whose upper bound is
     * excluded and whose bounds are interpreted as in [compareTo].
     *
     * Provided for call sites that can't bring the `in` operator into scope, such as Groovy build scripts.
     * @throws IllegalArgumentException if either bound can't be read as a single version
     */
    public infix fun within(range: OpenEndRange<*>): Boolean =
        this >= ParsedVersion(range.start) && this < ParsedVersion(range.endExclusive)

    /**Factories creating versions from values of an unknown type.*/
    public companion object {
        /**
         * Creates a parsed version from any [value] this type can interpret.
         * @throws IllegalArgumentException if [value] can't be read as a single version
         */
        public operator fun invoke(value: Any?): ParsedVersion = when (val target = VersionTarget.interpret(value)) {
            is VersionTarget.Exact -> ParsedVersion(target.version)
            is VersionTarget.Match -> invalidArg("Expected a version, but '$value' is a predicate")
        }

        /**Creates a parsed version from any [value] this type can interpret, or `null` if it can't.*/
        public fun parseOrNull(value: Any?): ParsedVersion? = when (val target = VersionTarget.interpretOrNull(value)) {
            is VersionTarget.Exact -> ParsedVersion(target.version)
            else -> null
        }
    }
}

private data class ClosedVersionRange(
    override val start: ParsedVersion,
    override val endInclusive: ParsedVersion,
) : ClosedRange<ParsedVersion>

private data class OpenVersionRange(
    override val start: ParsedVersion,
    override val endExclusive: ParsedVersion,
) : OpenEndRange<ParsedVersion>

private sealed interface VersionTarget {
    @JvmInline value class Exact(val version: Version) : VersionTarget
    @JvmInline value class Match(val predicates: List<VersionPredicate>) : VersionTarget {
        fun only(source: Any?): Version = requireNotNull(predicates.singleOrNull()?.version) {
            "Can't order a version against several predicates in '$source'"
        }
    }

    companion object {
        private const val UNWRAP_LIMIT: Int = 8

        fun interpretOrNull(value: Any?): VersionTarget? = runCatching { interpret(value) }.getOrNull()

        fun interpret(value: Any?): VersionTarget = when (val target = unwrap(value)) {
            is ParsedVersion -> Exact(target.value)
            is Version -> Exact(target)
            is StonecutterProject -> Exact(target.parsed.value)
            is CharSequence -> interpret(target.toString())
            is Number -> Exact(LenientOperations.parseAsPredicate(target.toString()))
            null -> invalidArg("Version target is null")
            else -> invalidArg("Unsupported version target of ${target::class.qualifiedName}")
        }

        private fun interpret(value: String): VersionTarget {
            val predicates = runCatching { arrayOf(value).unpack(VersionPredicate) }
                .getOrElse { throw it.takeAs<VersionParsingException>().asIllegalArg(value) }
            return when (predicates.size) {
                0 -> invalidArg("Version target is blank")
                1 if (predicates.first().operator == VersionOperator.IMPLICIT_EQUAL) -> Exact(predicates.first().version)
                else -> Match(predicates)
            }
        }

        private tailrec fun unwrap(value: Any?, rem: Int = UNWRAP_LIMIT): Any? = if (rem == 0) value else when (value) {
            is Provider<*> -> unwrap(value.orNull, rem - 1)
            is Optional<*> -> unwrap(value.getOrNull(), rem - 1)
            is Result<*> -> unwrap(value.getOrNull(), rem - 1)
            is Lazy<*> -> unwrap(value.value, rem - 1)
            else -> value
        }
    }
}

private fun computeHashCode(version: Version): Int = when (version) {
    is StringVersion -> version.hashCode()
    is SemanticVersion -> {
        var hash = 7
        var seenNotZero = false
        for (it in version.components) when (it) {
            0 if seenNotZero -> break
            else -> {
                if (it != 0) seenNotZero = true
                hash = 31 * hash + it
            }
        }
        hash
    }
}
