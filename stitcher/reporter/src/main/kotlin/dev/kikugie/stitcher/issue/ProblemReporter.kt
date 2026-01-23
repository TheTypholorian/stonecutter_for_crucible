package dev.kikugie.stitcher.issue

import java.nio.file.Path

@DslMarker
@Retention(AnnotationRetention.SOURCE)
private annotation class ProblemDsl

@ProblemDsl
interface ProblemReporter {
    val hasFailed: Boolean
    fun accept(report: ProblemReport)
}

interface FileProblemReporter : ProblemReporter {
    val file: Path
}

inline fun ProblemReporter.report(id: ProblemID, location: ProblemLocation? = null, builder: ProblemReport.() -> Unit): ProblemReport =
    ProblemReport.create(id, location, builder).also(::accept)

inline fun ProblemReporter.bail(id: ProblemID, location: ProblemLocation? = null, builder: ProblemReport.() -> Unit): Nothing =
    report(id, location, builder).let { throw BailException() }

inline fun FileProblemReporter.report(id: ProblemID, line: Int, column: Int, builder: ProblemReport.() -> Unit): ProblemReport =
    report(id, ProblemLocation(this.file, line, column), builder)

inline fun FileProblemReporter.report(id: ProblemID, index: Int, builder: ProblemReport.() -> Unit): ProblemReport =
    report(id, ProblemLocation(this.file, fileIndex = index), builder)

inline fun FileProblemReporter.bail(id: ProblemID, line: Int, column: Int, builder: ProblemReport.() -> Unit): Nothing =
    bail(id, ProblemLocation(this.file, line, column), builder)

inline fun FileProblemReporter.bail(id: ProblemID, index: Int, builder: ProblemReport.() -> Unit): Nothing =
    bail(id, ProblemLocation(this.file, fileIndex = index), builder)

inline fun <T> runBailing(action: () -> T): Result<T> = try {
    Result.success(action())
} catch (bail: BailException) {
    throw bail
} catch (e: Exception) {
    Result.failure(e)
}

inline fun <R, T> R.runBailingOn(action: R.() -> T): Result<T> = try {
    Result.success(action())
} catch (bail: BailException) {
    throw bail
} catch (e: Exception) {
    Result.failure(e)
}

inline fun <T : Any> tryBailing(action: () -> T): Result<T?> = try {
    Result.success(action())
} catch (_: BailException) {
    Result.success(null)
} catch (e: Exception) {
    Result.failure(e)
}

inline fun <R, T : Any> R.tryBailingOn(action: R.() -> T): Result<T?> = try {
    Result.success(action())
} catch (_: BailException) {
    Result.success(null)
} catch (e: Exception) {
    Result.failure(e)
}
