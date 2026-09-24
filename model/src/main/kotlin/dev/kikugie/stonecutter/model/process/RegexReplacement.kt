package dev.kikugie.stonecutter.model.process

import dev.kikugie.stonecutter.model.Identifier
import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

@Serializable @JvmRecord
public data class RegexReplacement(
    val pattern: String, val target: String, val flags: Int = 0,
    override val identifier: Identifier? = null,
) : Replacement, JavaSerializable
