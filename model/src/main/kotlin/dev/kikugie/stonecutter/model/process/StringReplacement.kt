package dev.kikugie.stonecutter.model.process

import dev.kikugie.stonecutter.model.Identifier
import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

@Serializable @JvmRecord
public data class StringReplacement(
    val pattern: String, val target: String,
    override val identifier: Identifier? = null,
) : Replacement, JavaSerializable
