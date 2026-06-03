@file:UseSerializers(RegexSerializer::class, PerlSerializer::class)

package dev.kikugie.stitcher.replacement

import dev.kikugie.stitcher.util.PerlSerializer
import dev.kikugie.stitcher.util.RegexSerializer
import dev.kikugie.stitcher.util.toEnumSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.pcre4j.Pcre2Code
import java.util.regex.Pattern

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

    val enabled: Boolean

}

/**
 * Replaces any of the given literal sources with the target string.
 *
 * @property target replacement output value
 * @property sources literal strings that are replaced by [target]
 * @property identifier optional identifier used to enable or disable this replacement
 * @property tags behavior modifiers for this replacement
 */
@Serializable
data class StringReplacement(
    val target: String,
    val pattern: String,
    override val identifier: String? = null,
    override val enabled: Boolean = false,
) : Replacement, java.io.Serializable

/**
 * Replaces text matching the regex pattern with the target string.
 *
 * @property target replacement output value
 * @property pattern regex pattern used to locate matches
 * @property identifier optional identifier used to enable or disable this replacement
 * @property tags behavior modifiers for this replacement
 */
@Serializable
data class RegexReplacement(
    val target: String,
    val pattern: String,
    val flags: Int = 0,
    override val identifier: String? = null,
    override val enabled: Boolean = false,
) : Replacement, java.io.Serializable {
    @Transient @kotlinx.serialization.Transient private lateinit var _regex: Pattern

    internal val regex: Pattern get() {
        if (!::_regex.isInitialized) _regex = Pattern.compile(pattern, flags)
        return _regex
    }

}

@Serializable
data class PerlReplacement(
    val target: String,
    val pattern: String,
    val options: Long = 0L,
    override val identifier: String? = null,
    override val enabled: Boolean = false,
) : Replacement, java.io.Serializable {
    @Transient @kotlinx.serialization.Transient private lateinit var _code: Pcre2Code

    internal val regex: Pcre2Code get() {
        if (!::_code.isInitialized) _code = Pcre2Code(pattern, options.toEnumSet())
        return _code
    }
}
