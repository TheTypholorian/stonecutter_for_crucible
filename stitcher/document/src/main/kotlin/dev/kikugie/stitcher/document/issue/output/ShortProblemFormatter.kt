package dev.kikugie.stitcher.document.issue.output

import dev.kikugie.stitcher.document.issue.ProblemReport
import dev.kikugie.stitcher.document.toUriString

object ShortProblemFormatter : ProblemFormatter {
    override fun format(report: ProblemReport): String =
        "${report.doc.toUriString(report.at)} ${report.label}"
}