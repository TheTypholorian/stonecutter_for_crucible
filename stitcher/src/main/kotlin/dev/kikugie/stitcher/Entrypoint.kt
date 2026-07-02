package dev.kikugie.stitcher

import dev.kikugie.stitcher.antlr.StitcherErrorListener
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.io.LineNormalizingReader
import dev.kikugie.stitcher.issue.BailException
import dev.kikugie.stitcher.issue.ProblemReport
import dev.kikugie.stitcher.issue.runMarked
import dev.kikugie.stitcher.parser.FileAstParser
import dev.kikugie.stitcher.parser.StitcherTokenFactory
import dev.kikugie.stitcher.token.eval.join
import dev.kikugie.stitcher.transformer.BlockTransformer
import dev.kikugie.stitcher.transformer.TransformParameters
import dev.kikugie.stitcher.util.errorListener
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedReader

fun process(name: String, string: String, parameters: TransformParameters, consumer: (ProblemReport) -> Unit): String =
    process(Path(name), CharStreams.fromString(string, name), parameters, consumer)

fun process(file: Path, parameters: TransformParameters, consumer: (ProblemReport) -> Unit): String {
    val input = CharStreams.fromReader(LineNormalizingReader(file.bufferedReader()), file.absolutePathString())
    return process(file, input, parameters, consumer)
}

fun process(file: Path, input: CharStream, parameters: TransformParameters, consumer: (ProblemReport) -> Unit): String {
    val document = Document(file, input, consumer)

    val source = parameters.adapter.create(input, document).apply {
        scanner.errorListener = StitcherErrorListener(document)
    }

    val layout = document.runMarked({ FileAstParser.parse(CommonTokenStream(source), document, StitcherTokenFactory) }) { root, ok ->
        if (ok) root else throw BailException()
    }

    return document.runMarked({ layout.accept(BlockTransformer(document, parameters, StitcherTokenFactory)) }) { root, ok ->
        if (ok) root.join() else throw BailException()
    }
}