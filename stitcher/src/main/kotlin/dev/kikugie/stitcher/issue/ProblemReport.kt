@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.issue

import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.DocumentLocation

@DslMarker
@Retention(AnnotationRetention.SOURCE)
private annotation class ReportDsl

@ReportDsl @JvmInline @JvmExposeBoxed @OptIn(ExperimentalStdlibApi::class)
value class ProblemID(val id: String) {
    inline operator fun plus(child: String): ProblemID = ProblemID("$id:$child")
    override fun toString(): String = id
}

@ReportDsl
class ProblemReport(
    val id: ProblemID,
    val doc: Document,
) {
    lateinit var label: String
    var info: String? = null
    var hint: String? = null
    var docs: String? = null
    var err: Throwable? = null
    var loc: DocumentLocation = DocumentLocation.UNKNOWN
    var sev: ProblemSeverity = ProblemSeverity.ERROR

    inline fun accept(formatter: ProblemFormatter): String =
        formatter.format(this)
}

@ReportDsl
enum class ProblemSeverity {
    INFO, WARN, ERROR
}
