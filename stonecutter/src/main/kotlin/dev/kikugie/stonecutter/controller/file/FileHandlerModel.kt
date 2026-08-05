package dev.kikugie.stonecutter.controller.file

import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.parser.ScannerAdapter
import dev.kikugie.stitcher.transformer.edit.CommentingStrategy
import dev.kikugie.stitcher.transformer.edit.SwappingStrategy
import dev.kikugie.stitcher.transformer.edit.UncommentingStrategy
import org.antlr.v4.runtime.CharStream
import java.io.Serializable

@JvmRecord
internal data class ScannerModel(
    val openers: IntArray,
    val closers: IntArray,
    val lexer: ScannerBuilder.LexerFactory
): Serializable, ScannerAdapter.Factory {
    constructor(builder: ScannerBuilder) : this(
        builder.openers.get().toIntArray(),
        builder.closers.get().toIntArray(),
        builder.lexer.get()
    )

    override fun create(input: CharStream, document: Document): ScannerAdapter =
        ScannerAdapter(lexer.create(input), openers, closers, document)

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
}