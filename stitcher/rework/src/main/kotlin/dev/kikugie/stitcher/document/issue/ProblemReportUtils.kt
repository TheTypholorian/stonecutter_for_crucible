@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.document.issue

import dev.kikugie.stitcher.document.DocumentReference

inline fun report(id: ProblemID, builder: ProblemReport.() -> Unit): Unit =
    ProblemReport(id).apply(builder).let { it.at.document.report(it) }

inline fun ProblemReporter.report(id: ProblemID, builder: ProblemReport.() -> Unit): Unit =
    ProblemReport(id).apply(builder).let(this::report)

inline fun DocumentReference.report(id: ProblemID, builder: ProblemReport.() -> Unit): Unit =
    document.report(id) inner@{ at = this@report; builder() }

inline fun ProblemReporter.bail(id: ProblemID, builder: ProblemReport.() -> Unit): Nothing {
    report(id, builder)
    throw BailException()
}

inline fun DocumentReference.bail(id: ProblemID, builder: ProblemReport.() -> Unit): Unit =
    document.bail(id) inner@{ at = this@bail; builder() }

inline fun <T, R> ProblemReporter.runMarked(action: ProblemReporter.() -> T, handler: (result: T, ok: Boolean) -> R): R {
    val marker = mark()
    return handler(action(), !marker.isDirty())
}

inline fun <T> ProblemReporter.runThrowing(action: ProblemReporter.() -> T): T {
    val marker = mark()
    return action().also { if (marker.isDirty()) throw BailException() }
}

inline fun <T> ProblemReporter.runCatching(action: ProblemReporter.() -> T): Result<T> = try {
    Result.success(runThrowing(action))
} catch (e: RuntimeException) {
    Result.failure(e)
}
