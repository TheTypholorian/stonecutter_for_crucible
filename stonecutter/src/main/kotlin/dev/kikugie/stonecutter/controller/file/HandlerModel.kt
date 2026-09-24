package dev.kikugie.stonecutter.controller.file

import dev.kikugie.stitcher.transformer.edit.CommentingStrategy
import dev.kikugie.stitcher.transformer.edit.SwappingStrategy
import dev.kikugie.stitcher.transformer.edit.UncommentingStrategy
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.security.MessageDigest

@JvmRecord
internal data class HandlerModel(
    val scanner: ScannerModel,
    val commenter: CommentingStrategy,
    val uncommenter: UncommentingStrategy,
    val swapper: SwappingStrategy
) : Serializable {
    constructor(builder: HandlerBuilder) : this(
        ScannerModel(builder.scanner.get()),
        builder.commenter.get(),
        builder.uncommenter.get(),
        builder.swapper.get()
    )

    fun fingerprint(): String {
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(this) } }.toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
    }
}
