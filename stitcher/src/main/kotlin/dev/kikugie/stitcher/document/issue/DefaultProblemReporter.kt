package dev.kikugie.stitcher.document.issue

import dev.kikugie.stitcher.document.issue.ProblemReport.Severity
import dev.kikugie.stitcher.document.issue.output.ProblemConsumer

class DefaultProblemReporter(private val reporter: ProblemConsumer, private val ignored: Set<ProblemID> = emptySet()) : ProblemReporter {
    override var errors: Long = 0L
        private set

    override fun report(report: ProblemReport) {
        if (report.severity == Severity.ERROR) errors++

        // Only non-critical reports may be ignored
        if (report.severity == Severity.ERROR || report.id !in ignored)
            reporter.accept(report)
    }
}
