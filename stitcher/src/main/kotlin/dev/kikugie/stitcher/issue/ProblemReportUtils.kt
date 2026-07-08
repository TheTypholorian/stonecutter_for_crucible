@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.issue

import dev.kikugie.stitcher.document.DocumentLocation

inline fun ProblemReporter.report(id: ProblemID, builder: ProblemReport.() -> Unit): Unit =
    create(id).apply(builder).let(::report)

inline fun ProblemReporter.bail(id: ProblemID, builder: ProblemReport.() -> Unit): Nothing {
    report(id, builder)
    throw BailException()
}

inline fun <T, R> ProblemReporter.runMarked(action: () -> T, handler: (result: T, ok: Boolean) -> R): R {
    val marker = mark()
    return handler(action(), !marker.isDirty)
}

inline fun <T> ProblemReporter.runThrowing(action: () -> T): T {
    val marker = mark()
    return action().also { if (marker.isDirty) throw BailException() }
}

context(report: ProblemReport)
inline fun DocumentLocation.link(): String = report.doc.link(this)
