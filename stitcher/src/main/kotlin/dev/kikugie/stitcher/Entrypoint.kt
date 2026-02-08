package dev.kikugie.stitcher

import dev.kikugie.stitcher.antlr.InlineErrorListener
import dev.kikugie.stitcher.antlr.charStream
import dev.kikugie.stitcher.issue.*
import dev.kikugie.stitcher.parser.StitcherTokenFactory
import dev.kikugie.stitcher.parser.layout.LayoutParser
import dev.kikugie.stitcher.token.eval.join
import dev.kikugie.stitcher.transformer.BlockTransformer
import dev.kikugie.stitcher.transformer.TransformParameters
import dev.kikugie.stitcher.util.errorListener
import org.antlr.v4.runtime.CommonTokenStream
import java.nio.file.Path
import kotlin.io.path.absolutePathString

fun process(file: Path, contents: String, parameters: TransformParameters, consumer: (ProblemReport) -> Unit): String {
    val input = contents.charStream(file.absolutePathString())
    val problems = ProblemReporterImpl(file, contents, consumer)
    val source = parameters.adapter.create(input, problems).apply {
        scanner.errorListener(InlineErrorListener(problems.location(0, 0, 0), problems))
    }

    val layout = problems.runMarked({ LayoutParser.parse(CommonTokenStream(source), input, StitcherTokenFactory, problems) }) { root, ok ->
        if (ok) root else throw BailException()
    }

    return problems.runMarked({ layout.accept(BlockTransformer(parameters, problems, input)) }) { root, ok ->
        if (ok) root.join() else throw BailException()
    }
}