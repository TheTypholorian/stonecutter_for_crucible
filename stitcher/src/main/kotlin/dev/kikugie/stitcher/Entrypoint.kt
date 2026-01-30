package dev.kikugie.stitcher

import dev.kikugie.stitcher.antlr.InlineErrorListener
import dev.kikugie.stitcher.antlr.charStream
import dev.kikugie.stitcher.issue.BailException
import dev.kikugie.stitcher.issue.ProblemReport
import dev.kikugie.stitcher.issue.ProblemReporterImpl
import dev.kikugie.stitcher.issue.location
import dev.kikugie.stitcher.issue.runMarked
import dev.kikugie.stitcher.parser.StitcherTokenFactory
import dev.kikugie.stitcher.parser.layout.LayoutParser
import dev.kikugie.stitcher.token.eval.join
import dev.kikugie.stitcher.transformer.BlockTransformer
import dev.kikugie.stitcher.transformer.InvalidParameterChecker
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

    val layout = problems.runMarked({ LayoutParser.parse(CommonTokenStream(source), input, StitcherTokenFactory, problems) }) {
        it.accept(InvalidParameterChecker(parameters, problems))
        throw BailException()
    }

    val modified = problems.runMarked({ layout.accept(BlockTransformer(parameters, StitcherTokenFactory, input, problems)) }) {
        throw BailException()
    }

    return modified.join()
}