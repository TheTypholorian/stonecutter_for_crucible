@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.util

import dev.kikugie.stitcher.antlr.StitcherErrorListener
import dev.kikugie.stitcher.antlr.errorListener
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.parser.FileAstParser
import dev.kikugie.stitcher.parser.FileTokenStream
import dev.kikugie.stitcher.parser.ScannerAdapter
import dev.kikugie.stitcher.token.BlockToken
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.TokenStream
import kotlin.io.path.absolutePathString

fun tokenize(document: Document, adapter: ScannerAdapter.Factory): CommonTokenStream {
    val input = CharStreams.fromString(document.toString(), document.file.absolutePathString())
    val scanner = adapter.create(input, document).apply {
        scanner.errorListener = StitcherErrorListener(document)
    }
    return CommonTokenStream(scanner)
}

fun parse(document: Document, stream: TokenStream): BlockToken.Root =
    FileAstParser.parse(FileTokenStream(document, stream))

inline fun parse(document: Document, adapter: ScannerAdapter.Factory): BlockToken.Root =
    parse(document, tokenize(document, adapter))
