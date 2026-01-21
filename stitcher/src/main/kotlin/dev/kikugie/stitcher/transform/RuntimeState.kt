package dev.kikugie.stitcher.transform

import dev.kikugie.stitcher.issue.ProblemReporter
import dev.kikugie.stitcher.transform.replacement.ReplacementProcessor
import org.antlr.v4.runtime.CharStream

internal class RuntimeState(val input: CharStream, val parameters: TransformParameters, val problems: ProblemReporter) {
    val replacer: ReplacementProcessor = ReplacementProcessor(parameters.replacements, problems)
}