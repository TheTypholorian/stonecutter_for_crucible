@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.document.issue

import dev.kikugie.stitcher.document.DocumentReference
import dev.kikugie.stitcher.document.issue.output.ProblemFormatter

@DslMarker
@Retention(AnnotationRetention.SOURCE)
private annotation class ReportDsl

@ReportDsl
class ProblemReport(val id: ProblemID) {
    lateinit var label: String
    lateinit var at: DocumentReference
    var info: String? = null
    var hint: String? = null
    var docs: String? = null
    var error: Throwable? = null
    var severity: Severity = Severity.ERROR

    inline fun accept(formatter: ProblemFormatter): String =
        formatter.format(this)

    enum class Severity {
        INFO, WARN, ERROR
    }
}