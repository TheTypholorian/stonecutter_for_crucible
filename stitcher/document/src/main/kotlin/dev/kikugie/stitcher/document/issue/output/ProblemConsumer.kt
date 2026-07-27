package dev.kikugie.stitcher.document.issue.output

import dev.kikugie.stitcher.document.issue.ProblemReport

fun interface ProblemConsumer {
    fun accept(report: ProblemReport)
}