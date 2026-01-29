@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.issue

import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.lineIndex
import dev.kikugie.stitcher.token.StitcherToken

inline fun FileProblemReporter.location(token: AntlrToken): ProblemLocation =
    ProblemLocation(this.file, token.lineIndex, token.charPositionInLine, token.startIndex)

inline fun FileProblemReporter.location(token: StitcherToken): ProblemLocation =
    locate(token.start)

inline fun FileProblemReporter.report(id: ProblemID, token: AntlrToken, builder: ProblemReport.() -> Unit): ProblemReport =
    report(id, token.lineIndex, token.charPositionInLine, builder)

inline fun FileProblemReporter.bail(id: ProblemID, token: AntlrToken, builder: ProblemReport.() -> Unit): Nothing =
    bail(id, token.lineIndex, token.charPositionInLine, builder)

inline fun FileProblemReporter.report(id: ProblemID, token: StitcherToken, builder: ProblemReport.() -> Unit): ProblemReport =
    report(id, token.start, builder)

inline fun FileProblemReporter.bail(id: ProblemID, token: StitcherToken, builder: ProblemReport.() -> Unit): Nothing =
    bail(id, token.start, builder)
