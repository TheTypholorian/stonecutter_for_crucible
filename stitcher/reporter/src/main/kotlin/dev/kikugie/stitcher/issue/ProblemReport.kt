package dev.kikugie.stitcher.issue

@DslMarker
@Retention(AnnotationRetention.SOURCE)
private annotation class ReportDsl

@ReportDsl @JvmInline @JvmExposeBoxed @OptIn(ExperimentalStdlibApi::class)
value class ProblemID(val id: String) {
    fun child(child: String): ProblemID = ProblemID("$id:$child")
}

@ReportDsl
class ProblemReport(
    val id: ProblemID,
    val location: ProblemLocation?,
) {
    lateinit var label: String
    var severity: ProblemSeverity = ProblemSeverity.ERROR
    var details: String? = null
    var solution: String? = null
    var documentation: String? = null
    var exception: Throwable? = null
//    val children: MutableList<ProblemReport> = mutableListOf()

    fun copy(id: ProblemID = this.id, location: ProblemLocation? = this.location): ProblemReport = ProblemReport(id, location).also {
        it.label = this.label
        it.severity = this.severity
        it.details = this.details
        it.solution = this.solution
        it.documentation = this.documentation
        it.exception = this.exception
//        it.children += this.children
    }

    companion object {
        inline fun create(id: ProblemID, location: ProblemLocation? = null, builder: ProblemReport.() -> Unit): ProblemReport =
            ProblemReport(id, location).apply(builder)
    }
}

@ReportDsl
enum class ProblemSeverity {
    INFO, WARN, ERROR
}
