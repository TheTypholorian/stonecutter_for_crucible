package dev.kikugie.stitcher.document.issue.output

import dev.kikugie.stitcher.document.issue.ProblemReport

fun interface ProblemFormatter {
    fun format(report: ProblemReport): String
}

