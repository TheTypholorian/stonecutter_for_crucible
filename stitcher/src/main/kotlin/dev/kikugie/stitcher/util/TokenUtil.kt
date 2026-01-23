package dev.kikugie.stitcher.util

import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.lineIndex
import dev.kikugie.stitcher.issue.*
import dev.kikugie.stitcher.token.StitcherToken

internal inline fun FileProblemReporter.report(id: ProblemID, token: AntlrToken, builder: ProblemReport.() -> Unit): ProblemReport =
    report(id, token.lineIndex, token.charPositionInLine, builder)

internal inline fun FileProblemReporter.bail(id: ProblemID, token: AntlrToken, builder: ProblemReport.() -> Unit): Nothing =
    bail(id, token.lineIndex, token.charPositionInLine, builder)

internal inline fun FileProblemReporter.report(id: ProblemID, token: StitcherToken, builder: ProblemReport.() -> Unit): ProblemReport =
    report(id, token.start, builder)

internal inline fun FileProblemReporter.bail(id: ProblemID, token: StitcherToken, builder: ProblemReport.() -> Unit): Nothing =
    bail(id, token.start, builder)
