package dev.kikugie.stonecutter

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.*

/** Marks a [String] as the name of a constant. */
@Target(VALUE_PARAMETER, LOCAL_VARIABLE, PROPERTY, FIELD, TYPE)
@Retention(BINARY)
public annotation class ConstantID

/** Marks a [String] as the name of a target. */
@Target(VALUE_PARAMETER, LOCAL_VARIABLE, PROPERTY, FIELD, TYPE)
@Retention(BINARY)
public annotation class TargetID

/** Marks a [String] as the name of a swap. */
@Target(VALUE_PARAMETER, LOCAL_VARIABLE, PROPERTY, FIELD, TYPE)
@Retention(BINARY)
public annotation class SwapID

/** Marks a [String] as the name of a replacement. */
@Target(VALUE_PARAMETER, LOCAL_VARIABLE, PROPERTY, FIELD, TYPE)
@Retention(BINARY)
public annotation class ReplacementID

/** Marks a [String] as a `SourceSet`-relative Ant pattern. */
@Target(VALUE_PARAMETER, LOCAL_VARIABLE, PROPERTY, FIELD, TYPE)
@Retention(BINARY)
public annotation class SourceSetPattern

/**
 * Marks a [String] interpreted as a version by Stonecutter.
 *
 * @property flags Additional constraints on the version
 */
@Target(VALUE_PARAMETER, LOCAL_VARIABLE, PROPERTY, FIELD, TYPE)
@Retention(BINARY)
public annotation class VersionString(vararg val flags: String) {
    public companion object {
        /** The string is parsed as an extended semantic version, rejecting lexicographically compared ones. */
        public const val SEMVER_ONLY: String = "semver-only"

        /** The string is parsed as a comparison target, supporting `-*` suffix if it's an extended semantic version. */
        public const val SUPPORTS_WILDCARD: String = "supports-wildcard"

        /** The string is parsed as a predicate, supporting operators like `>=`, `~`, `!=`, etc. */
        public const val SUPPORTS_OPERATORS: String = "supports-operators"

        /** The version may be registered in Stonecutter, which allows variant lookup. */
        public const val STONECUTTER_KNOWN: String = "stonecutter-known"
    }
}

/**
 * Applies additional properties to version strings in the annotated class, function, or property.
 *
 * @property include Flags to merge with the strings annotated with [VersionString]
 * @property exclude Flags to remove from the strings annotated with [VersionString]
 */
@Target(CLASS, FUNCTION, PROPERTY, PROPERTY_GETTER)
@Retention(BINARY)
public annotation class VersionStringMeta(val include: Array<String> = [], val exclude: Array<String> = [])
