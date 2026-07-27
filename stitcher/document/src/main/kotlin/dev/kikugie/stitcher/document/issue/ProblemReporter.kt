@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.document.issue

@DslMarker
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
private annotation class ProblemDsl

@ProblemDsl
interface ProblemReporter {
    fun mark(): Marker
    fun create(id: ProblemID): ProblemReport
    fun report(report: ProblemReport)

    fun interface Marker {
        fun isDirty(): Boolean
    }
}
