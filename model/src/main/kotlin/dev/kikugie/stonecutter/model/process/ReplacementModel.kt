package dev.kikugie.stonecutter.model.process

import dev.kikugie.stonecutter.model.FilePattern
import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

@Serializable @JvmRecord
public data class ReplacementModel(
    val replacement: Replacement,
    val enabled: Boolean,
    val includes: Set<FilePattern> = emptySet(),
    val excludes: Set<FilePattern> = emptySet(),
) : JavaSerializable
