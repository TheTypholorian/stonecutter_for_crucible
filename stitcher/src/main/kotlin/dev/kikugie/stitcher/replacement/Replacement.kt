@file:UseSerializers(RegexSerializer::class, PerlSerializer::class, PerlCompileOptionsSerializer::class)

package dev.kikugie.stitcher.replacement

import dev.kikugie.stitcher.util.PerlCompileOptionsSerializer
import dev.kikugie.stitcher.util.PerlSerializer
import dev.kikugie.stitcher.util.RegexSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.pcre4j.Pcre2Code
import org.pcre4j.option.Pcre2CompileOption
import java.util.*
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
    val regex: Pattern by lazy { Pattern.compile(pattern, flags) }
}

@Serializable
data class PerlReplacement(
    val target: String,
    val pattern: String,
    val options: EnumSet<Pcre2CompileOption>,
    override val identifier: String? = null,
    override val enabled: Boolean = false,
) : Replacement, java.io.Serializable {
    val regex: Pcre2Code by lazy { Pcre2Code(pattern, options) }
}
