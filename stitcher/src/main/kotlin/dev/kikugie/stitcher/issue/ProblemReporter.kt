@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.issue

import dev.kikugie.stitcher.document.Document

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

    interface Marker {
        val isDirty: Boolean
    }
}

@ProblemDsl
internal class ProblemReporterImpl(private val doc: Document, private val consumer: ProblemConsumer) : ProblemReporter {
    private var counter: Int = 0

    override fun mark(): Marker = Marker(counter)
    override fun create(id: ProblemID): ProblemReport = ProblemReport(id, doc)

    override fun report(report: ProblemReport) {
        counter++
        consumer.accept(report)
    }

    @ProblemDsl
    inner class Marker internal constructor(private val value: Int) : ProblemReporter.Marker {
        override val isDirty: Boolean get() = value < this@ProblemReporterImpl.counter
    }
}
