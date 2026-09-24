package dev.kikugie.stonecutter.model.process

import dev.kikugie.semver.data.Version
import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

@Serializable @JvmRecord
public data class ProcessParameters(
    val constants: Map<String, Boolean> = emptyMap(),
    val targets: Map<String, Version> = emptyMap(),
    val swaps: Map<String, String> = emptyMap(),
    val replacements: List<ReplacementModel> = emptyList(),
) : JavaSerializable
