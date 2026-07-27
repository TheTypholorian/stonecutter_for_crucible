package dev.kikugie.stitcher.document.issue.output

import dev.kikugie.stitcher.document.issue.ProblemReport

object ShortProblemFormatter : ProblemFormatter {
    override fun format(report: ProblemReport): String =
        "${report.at.toUriString()} ${report.label}"
}