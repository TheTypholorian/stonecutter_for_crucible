package dev.kikugie.stonecutter.controller.file

import dev.kikugie.stitcher.antlr.charStream
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.parser.ScannerAdapter
import java.io.Serializable

@JvmRecord
internal data class ScannerModel(
    val openers: IntArray,
    val closers: IntArray,
    val lexer: ScannerBuilder.LexerFactory
) : Serializable, ScannerAdapter.Factory {
    constructor(builder: ScannerBuilder) : this(
        builder.openers.get().toIntArray(),
        builder.closers.get().toIntArray(),
        builder.lexer.get()
    )

    override fun create(document: Document): ScannerAdapter =
        ScannerAdapter(lexer.create(document.charStream()), openers, closers, document)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScannerModel) return false

        if (!openers.contentEquals(other.openers)) return false
        if (!closers.contentEquals(other.closers)) return false
        if (lexer != other.lexer) return false

        return true
    }

    override fun hashCode(): Int {
        var result = openers.contentHashCode()
        result = 31 * result + closers.contentHashCode()
        result = 31 * result + lexer.hashCode()
        return result
    }
}
