package dev.kikugie.stitcher.issue

fun interface ProblemFormatter {
    fun format(report: ProblemReport): String
}

object FullProblemFormatter : ProblemFormatter {
    override fun format(report: ProblemReport): String = buildString {
        append(report.prefix, ' ', report.location?.toLinkString() ?: "<unknown file>", ' ', report.label, '\n')
        for ((index, line) in report.details?.lineSequence().orEmpty().withIndex())
            append(if (index == 0) "> " else "| ", line, '\n')
        report.hint?.let { append("Hint: ", it, '\n') }
        report.docs?.let { append("Docs: ", it, '\n') }
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
}

object ShortProblemFormatter : ProblemFormatter {
    override fun format(report: ProblemReport): String =
        "${report.location?.toLinkString() ?: "<unknown file>"} ${report.label}"
}