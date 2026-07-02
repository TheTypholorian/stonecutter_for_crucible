package dev.kikugie.stitcher

import dev.kikugie.stitcher.antlr.StitcherErrorListener
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.issue.BailException
import dev.kikugie.stitcher.issue.runMarked
import dev.kikugie.stitcher.parser.FileAstParser
import dev.kikugie.stitcher.parser.StitcherTokenFactory
import dev.kikugie.stitcher.token.eval.join
import dev.kikugie.stitcher.transformer.BlockTransformer
import dev.kikugie.stitcher.transformer.TransformParameters
import dev.kikugie.stitcher.util.errorListener
import org.antlr.v4.runtime.CommonTokenStream

fun process(document: Document, parameters: TransformParameters): String {
    val source = parameters.adapter.create(document.source, document).apply {
        scanner.errorListener = StitcherErrorListener(document)
    }

    val layout = document.runMarked({ FileAstParser.parse(CommonTokenStream(source), document, StitcherTokenFactory) }) { root, ok ->
        if (ok) root else throw BailException()
    }

    return document.runMarked({ layout.accept(BlockTransformer(document, parameters, StitcherTokenFactory)) }) { root, ok ->
        if (ok) root.join() else throw BailException()
    }
}