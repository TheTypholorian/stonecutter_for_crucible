@file:Suppress("NOTHING_TO_INLINE", "unused")

package dev.kikugie.stitcher.issue

import java.nio.file.Path

@DslMarker
@Retention(AnnotationRetention.SOURCE)
private annotation class ProblemDsl

@ProblemDsl
interface ProblemMarker {
    val isDirty: Boolean
}

/**
 * Receives problem reports during processing
 */
@ProblemDsl
interface ProblemReporter {
    fun mark(): ProblemMarker
    /**
     * Accepts the given [report] for handling
     */
    fun accept(report: ProblemReport)
}

/**
 * Reporter that is scoped to a single file
 */
@ProblemDsl
interface FileProblemReporter : ProblemReporter {
    /**
     * File associated with all reported problems
     */
    val file: Path

    fun locate(index: Int): ProblemLocation
}

/**
 * Creates a location within the associated [file][FileProblemReporter.file] using [line], [column], and [index]
 */
inline fun FileProblemReporter.location(line: Int = -1, column: Int = -1, index: Int = -1): ProblemLocation =
    ProblemLocation(this.file, line, column, index)

/**
 * Builds and reports a problem identified by [id] at [location] using [builder]
 */
inline fun ProblemReporter.report(id: ProblemID, location: ProblemLocation? = null, builder: ProblemReport.() -> Unit): ProblemReport =
    ProblemReport.create(id, location, builder).also(::accept)

/**
 * Reports a problem identified by [id] at [location] and then throws [BailException]
 */
inline fun ProblemReporter.bail(id: ProblemID, location: ProblemLocation? = null, builder: ProblemReport.() -> Unit): Nothing =
    report(id, location, builder).let { throw BailException() }

/**
 * Builds and reports a problem identified by [id] at [line] and [column] using [builder]
 */
inline fun FileProblemReporter.report(id: ProblemID, line: Int, column: Int, builder: ProblemReport.() -> Unit): ProblemReport =
    report(id, location(line, column), builder)

/**
 * Builds and reports a problem identified by [id] at [index] using [builder]
 */
inline fun FileProblemReporter.report(id: ProblemID, index: Int, builder: ProblemReport.() -> Unit): ProblemReport =
    report(id, location(index = index), builder)

/**
 * Reports a problem identified by [id] at [line] and [column] and then throws [BailException]
 */
inline fun FileProblemReporter.bail(id: ProblemID, line: Int, column: Int, builder: ProblemReport.() -> Unit): Nothing =
    bail(id, location(line, column), builder)

/**
 * Reports a problem identified by [id] at [index] and then throws [BailException]
 */
inline fun FileProblemReporter.bail(id: ProblemID, index: Int, builder: ProblemReport.() -> Unit): Nothing =
    bail(id, location(index = index), builder)

/**
 * Runs [action] and captures non-bail exceptions into a [Result]
 */
inline fun <T> runBailing(action: () -> T): Result<T> = try {
    Result.success(action())
} catch (bail: BailException) {
    throw bail
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * Runs [action] on the receiver and captures non-bail exceptions into a [Result]
 */
inline fun <R, T> R.runBailingOn(action: R.() -> T): Result<T> = try {
    Result.success(action())
} catch (bail: BailException) {
    throw bail
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * Runs [action] and returns null on [BailException]
 */
inline fun <T : Any> tryBailing(action: () -> T): Result<T?> = try {
    Result.success(action())
} catch (_: BailException) {
    Result.success(null)
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * Runs [action] on the receiver and returns null on [BailException]
 */
inline fun <R, T : Any> R.tryBailingOn(action: R.() -> T): Result<T?> = try {
    Result.success(action())
} catch (_: BailException) {
    Result.success(null)
} catch (e: Exception) {
    Result.failure(e)
}

inline fun <T> ProblemReporter.runMarked(action: () -> T, handler: (T) -> Nothing): T {
    val marker = mark()
    val result = action()
    return if (marker.isDirty) handler(result) else result
}
