package dev.kikugie.stitcher.replacement

import kotlinx.serialization.Serializable

/**
 * Describes a replacement rule applied by the replacer pipeline.
 *
 * Implementations define a target value and one or more matching sources, optionally gated by an identifier
 * and tag-based behavior flags.
 */
@Serializable
sealed interface Replacement : java.io.Serializable {
    /**
     * Optional identifier used to toggle this replacement at runtime.
     */
    val identifier: String?
    /**
     * Behavior modifiers for this replacement.
     */
    val tags: Set<Tag>


    /**
     * Flags controlling how a replacement is interpreted by the executor.
     */
    enum class Tag {
        DEFAULT_ENABLED, CASE_INSENSITIVE, WORD_ONLY
    }

}

/**
 * Replaces any of the given literal sources with the target string.
 *
 * @property target replacement output value
 * @property sources literal strings that are replaced by [target]
 * @property identifier optional identifier used to enable or disable this replacement
 * @property tags behavior modifiers for this replacement
 */
@Serializable @JvmRecord
data class StringReplacement(
    val target: String,
    val sources: Set<String>,
    override val identifier: String? = null,
    override val tags: Set<Replacement.Tag> = emptySet(),
) : Replacement, java.io.Serializable {
}

/**
 * Replaces text matching the regex pattern with the target string.
 *
 * @property target replacement output value
 * @property pattern regex pattern used to locate matches
 * @property identifier optional identifier used to enable or disable this replacement
 * @property tags behavior modifiers for this replacement
 */
@Serializable @JvmRecord
data class RegexReplacement(
    val target: String,
    val pattern: String,
    override val identifier: String? = null,
    override val tags: Set<Replacement.Tag> = emptySet(),
) : Replacement, java.io.Serializable {
    /**
     * Compiled regex for [pattern].
     */
    val regex: Regex get() = Regex(pattern)
}
