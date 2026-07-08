@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.issue

@DslMarker
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
private annotation class ProblemDsl

@ProblemDsl
fun interface ProblemConsumer {
    fun accept(report: ProblemReport)
}

@ProblemDsl
interface ProblemReporter {
    fun mark(): Marker
    fun create(id: ProblemID): ProblemReport
    fun report(report: ProblemReport)

    fun interface Marker {
        fun isDirty(): Boolean
    }
}
