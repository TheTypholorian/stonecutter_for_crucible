package dev.kikugie.stitcher.document.issue.output

import dev.kikugie.stitcher.document.issue.ProblemReport

object FullProblemFormatter : ProblemFormatter {
    override fun format(report: ProblemReport): String = buildString {
        appendLine("${report.prefix}: ${report.at.toUriString()} ${report.decorateLabel()}")
        report.info?.let { appendLines(it, "> ") }
        report.hint?.let { appendLines(it.replaceFirstChar(Char::lowercaseChar), "- Hint: ") }
        report.docs?.let { appendLines(it.replaceFirstChar(Char::lowercaseChar), "- Docs: ") }
        report.error?.let {
            append("Caused by: ", it::class.simpleName ?: "<anonymous class>", ' ', it.message.orEmpty(), '\n', it.stackTraceToString())
        }
    }

    private val ProblemReport.prefix: Char
        get() = when (severity) {
            ProblemReport.Severity.INFO -> 'i'
            ProblemReport.Severity.WARN -> 'w'
            ProblemReport.Severity.ERROR -> 'e'
        }

    private fun ProblemReport.decorateLabel(): String =
        if (!at.location.line.rhymes()) label
        else "~\n\u001B[41m\u001B[97mRoses are red\u001B[0m; \u001B[46m\u001B[97mViolets are blue\u001B[0m; $label on line ${at.location.line}"

    private fun StringBuilder.appendLines(text: String, init: String, follow: String = "  ") {
        for ((index, line) in text.lineSequence().withIndex())
            append(if (index == 0) init else follow, line, '\n')
    }

    // Twelves don't rhyme
    // The "on line two" ends the rhyme too abruptly
    private fun Int.rhymes(): Boolean =
        this > 21 && this % 10 == 2 && this % 100 != 12
}