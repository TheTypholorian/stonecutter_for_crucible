package dev.kikugie.stonecutter.model.process

import dev.kikugie.semver.data.Version
import dev.kikugie.stonecutter.model.LenientListSerializer
import dev.kikugie.stonecutter.model.LenientMapSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import java.io.Serializable as JavaSerializable

@Serializable @JvmRecord
public data class ProcessParameters(
    val constants: Map<String, Boolean> = emptyMap(),
    @Serializable(with = TargetsSerializer::class)
    val targets: Map<String, Version> = emptyMap(),
    val swaps: Map<String, String> = emptyMap(),
    @Serializable(with = ReplacementsSerializer::class)
    val replacements: List<ReplacementModel> = emptyList(),
    val files: FilesModel = FilesModel(),
) : JavaSerializable

private object TargetsSerializer : LenientMapSerializer<Version>(serializer(), "target")

private object ReplacementsSerializer : LenientListSerializer<ReplacementModel>(ReplacementModel.serializer(), "replacement")
