package dev.kikugie.stonecutter.model.process

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

@Serializable @JvmRecord
public data class ReplacementModel(
    val replacement: Replacement,
    val enabled: Boolean,
    val includes: Set<String> = emptySet(),
    val excludes: Set<String> = emptySet(),
) : JavaSerializable
