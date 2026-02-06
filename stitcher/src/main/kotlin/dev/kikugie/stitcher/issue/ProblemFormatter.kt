package dev.kikugie.stitcher.issue

fun interface ProblemFormatter {
    fun format(report: ProblemReport): String
}

object FullProblemFormatter : ProblemFormatter {
    override fun format(report: ProblemReport): String = buildString {
        append(report.prefix, ": ", report.location?.toLinkString() ?: "<unknown file>", ' ', report.label, '\n')
        report.details?.let { appendLines(it, "> ") }
        report.hint?.let { appendLines(it, "- Hint: ") }
        report.docs?.let { appendLines(it, "- Docs: ") }
        report.exception?.let {
            append("Caused by: ", it::class.simpleName ?: "<anonymous class>", ' ', it.message.orEmpty(), '\n', it.stackTraceToString())
        }
//        if (endsWith('\n'))
//            replace(length - 1, length, "")
    }

    private val ProblemReport.prefix: Char
        get() = when (severity) {
            ProblemSeverity.INFO -> 'i'
            ProblemSeverity.WARN -> 'w'
            ProblemSeverity.ERROR -> 'e'
        }

    private fun StringBuilder.appendLines(text: String, init: String, follow: String = "  ") {
        for ((index, line) in text.lineSequence().withIndex())
            append(if (index == 0) init else follow, line, '\n')
    }
}

object ShortProblemFormatter : ProblemFormatter {
    override fun format(report: ProblemReport): String =
        "${report.location?.toLinkString() ?: "<unknown file>"} ${report.label}"
}