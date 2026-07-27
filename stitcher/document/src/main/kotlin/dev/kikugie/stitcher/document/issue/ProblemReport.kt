@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.document.issue

import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.DocumentLocation
import dev.kikugie.stitcher.document.issue.output.ProblemFormatter

@DslMarker
@Retention(AnnotationRetention.SOURCE)
private annotation class ReportDsl

@ReportDsl
class ProblemReport(val id: ProblemID, val doc: Document) {
    lateinit var label: String
    var info: String? = null
    var hint: String? = null
    var docs: String? = null
    var error: Throwable? = null
    var at: DocumentLocation = DocumentLocation.UNKNOWN
    var severity: Severity = Severity.ERROR

    inline fun accept(formatter: ProblemFormatter): String =
        formatter.format(this)

    enum class Severity {
        INFO, WARN, ERROR
    }
}