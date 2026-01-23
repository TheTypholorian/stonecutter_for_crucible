package dev.kikugie.stitcher.replacement

import kotlinx.serialization.Serializable

@Serializable
sealed interface Replacement : java.io.Serializable {
    val identifier: String?
    val tags: Set<Tag>


    enum class Tag {
        DEFAULT_ENABLED, CASE_INSENSITIVE, WORD_ONLY
    }

}

@Serializable
data class StringReplacement(
    val target: String,
    val sources: Set<String>,
    override val identifier: String? = null,
    override val tags: Set<Replacement.Tag> = emptySet(),
) : Replacement {
    constructor(target: String, vararg sources: String, identifier: String? = null)
        : this(target, sources.toSet(), identifier)
}

@Serializable
data class RegexReplacement(
    val target: String,
    val pattern: String,
    override val identifier: String? = null,
    override val tags: Set<Replacement.Tag> = emptySet(),
) : Replacement {
    val regex: Regex get() = Regex(pattern)
}
