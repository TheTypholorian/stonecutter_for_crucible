package dev.kikugie.stitcher.issue

@DslMarker
@Retention(AnnotationRetention.SOURCE)
private annotation class ReportDsl

/**
 * Identifies a reportable problem in a hierarchical namespace
 */
@ReportDsl @JvmInline @JvmExposeBoxed @OptIn(ExperimentalStdlibApi::class)
value class ProblemID(val id: String) {
    /**
     * Creates a child identifier by appending a segment
     */
    fun child(child: String): ProblemID = ProblemID("$id:$child")

    override fun toString(): String = id
}

/**
 * Describes a reported problem and its optional context
 */
@ReportDsl
class ProblemReport(
    /**
     * Unique identifier for this problem type
     */
    val id: ProblemID,
    /**
     * Location where the problem was detected, if known
     */
    val location: ProblemLocation?,
) {
    /**
     * Short human-readable label for the problem
     */
    lateinit var label: String

    /**
     * Severity level for the problem
     */
    var severity: ProblemSeverity = ProblemSeverity.ERROR

    /**
     * Optional detailed explanation
     */
    var details: String? = null

    /**
     * Optional suggested hint
     */
    var hint: String? = null

    /**
     * Optional documentation link or reference
     */
    var docs: String? = null

    /**
     * Optional underlying exception
     */
    var exception: Throwable? = null
//    val children: MutableList<ProblemReport> = mutableListOf()

    /**
     * Copies this report while optionally overriding its id or location
     */
    fun copy(id: ProblemID = this.id, location: ProblemLocation? = this.location): ProblemReport = ProblemReport(id, location).also {
        it.label = this.label
        it.severity = this.severity
        it.details = this.details
        it.hint = this.hint
        it.docs = this.docs
        it.exception = this.exception
//        it.children += this.children
    }

    fun accept(formatter: ProblemFormatter): String =
        formatter.format(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProblemReport) return false

        if (id != other.id) return false
        if (location != other.location) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (location?.hashCode() ?: 0)
        return result
    }

    companion object {
        /**
         * Creates a report and applies the provided builder
         */
        inline fun create(id: ProblemID, location: ProblemLocation? = null, builder: ProblemReport.() -> Unit): ProblemReport =
            ProblemReport(id, location).apply(builder)
    }
}

/**
 * Severity levels for reported problems
 */
@ReportDsl
enum class ProblemSeverity {
    INFO, WARN, ERROR
}
