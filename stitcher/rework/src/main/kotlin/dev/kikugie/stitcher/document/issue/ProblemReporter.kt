@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.document.issue

@DslMarker
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
private annotation class ProblemDsl

@ProblemDsl
interface ProblemReporter {
    val errors: Long
    fun report(report: ProblemReport)
}
