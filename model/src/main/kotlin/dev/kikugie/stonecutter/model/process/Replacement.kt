package dev.kikugie.stonecutter.model.process

import dev.kikugie.stonecutter.model.Identifier
import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

@Serializable
public sealed interface Replacement {
    public val identifier: Identifier?
    public val defaultEnabled: Boolean
}

@Serializable @JvmRecord
public data class StringReplacement(
    val pattern: String, val target: String,
    override val identifier: Identifier? = null,
    override val defaultEnabled: Boolean = false,
) : Replacement, JavaSerializable

@Serializable @JvmRecord
public data class RegexReplacement(
    val pattern: String, val target: String, val flags: Int = 0,
    override val identifier: Identifier? = null,
    override val defaultEnabled: Boolean = false,
) : Replacement, JavaSerializable

@Serializable @JvmRecord
public data class PerlReplacement(
    val pattern: String, val target: String, val flags: Int = 0,
    override val identifier: Identifier? = null,
    override val defaultEnabled: Boolean = false,
) : Replacement, JavaSerializable
