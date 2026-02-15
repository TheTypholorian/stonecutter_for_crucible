package dev.kikugie.stitcher.issue

fun interface ProblemFormatter {
    fun format(report: ProblemReport): String
}

object FullProblemFormatter : ProblemFormatter {
    override fun format(report: ProblemReport): String = buildString {
        append(report.prefix, ": ", report.location?.toLinkString() ?: "<unknown file>", ' ', report.decorateLabel(), '\n')
        report.details?.let { appendLines(it, "> ") }
        report.hint?.let { appendLines(it.replaceFirstChar(Char::lowercaseChar), "- Hint: ") }
        report.docs?.let { appendLines(it.replaceFirstChar(Char::lowercaseChar), "- Docs: ") }
        report.exception?.let {
            append("Caused by: ", it::class.simpleName ?: "<anonymous class>", ' ', it.message.orEmpty(), '\n', it.stackTraceToString())
        }
    }

    private val ProblemReport.prefix: Char
        get() = when (severity) {
            ProblemSeverity.INFO -> 'i'
            ProblemSeverity.WARN -> 'w'
            ProblemSeverity.ERROR -> 'e'
        }

    private fun ProblemReport.decorateLabel(): String =
        if (!(location?.line ?: 0).rhymes()) label
        else "~\n\u001B[41mRoses are red\u001B[0m; \u001B[46mViolets are blue\u001B[0m; $label on line ${location?.line}"

    private fun StringBuilder.appendLines(text: String, init: String, follow: String = "  ") {
        for ((index, line) in text.lineSequence().withIndex())
            append(if (index == 0) init else follow, line, '\n')
    }

    // Twelves don't rhyme
    // The "on line two" ends the rhyme too abruptly
    private fun Int.rhymes(): Boolean =
        this > 21 && this % 10 == 2 && this % 100 != 12
}

object ShortProblemFormatter : ProblemFormatter {
    override fun format(report: ProblemReport): String =
        "${report.location?.toLinkString() ?: "<unknown file>"} ${report.label}"
}