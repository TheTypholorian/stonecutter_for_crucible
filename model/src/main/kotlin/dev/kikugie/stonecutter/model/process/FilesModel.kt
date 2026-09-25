package dev.kikugie.stonecutter.model.process

import dev.kikugie.stonecutter.model.FilePattern
import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

@Serializable @JvmRecord
public data class FilesModel(
    val includes: Set<FilePattern> = emptySet(),
    val excludes: Set<FilePattern> = emptySet(),
    val disabled: Set<FilePattern> = emptySet(),
    val enabled: Set<FilePattern> = emptySet(),
) : JavaSerializable
