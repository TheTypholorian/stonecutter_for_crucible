package dev.kikugie.stitcher.issue

import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.length
import dev.kikugie.stitcher.util.AntlrToken
import dev.kikugie.stitcher.util.length
import java.nio.file.Path

@DslMarker
@Retention(AnnotationRetention.SOURCE)
private annotation class ProblemDsl

@ProblemDsl
public interface ProblemReporter {
    public val file: Path
    public fun accept(report: ProblemReport)
    public fun locate(index: Int): ProblemLocation
}

public val AntlrToken.location: ProblemLocation
    get() = ProblemLocation(line, charPositionInLine + 1)

public inline fun ProblemReporter.report(id: ProblemID, location: ProblemLocation, length: Int = 0, builder: ProblemReport.() -> Unit): Unit =
    ProblemReport(id, file, location, length).apply(builder).let(::accept)

public inline fun ProblemReporter.report(id: ProblemID, index: Int, length: Int = 0, builder: ProblemReport.() -> Unit): Unit =
    report(id, locate(index), length, builder)

internal inline fun ProblemReporter.report(id: ProblemID, token: AntlrToken, builder: ProblemReport.() -> Unit): Unit =
    report(id, token.location, token.length, builder)

internal inline fun ProblemReporter.report(id: ProblemID, token: StitcherToken, builder: ProblemReport.() -> Unit): Unit =
    report(id, token.start, token.length, builder)

public inline fun ProblemReporter.bail(id: ProblemID, location: ProblemLocation, length: Int = 0, builder: ProblemReport.() -> Unit): Nothing {
    report(id, location, length, builder)
    throw BailException()
}

public inline fun ProblemReporter.bail(id: ProblemID, index: Int, length: Int = 0, builder: ProblemReport.() -> Unit): Nothing =
    bail(id, locate(index), length, builder)

internal inline fun ProblemReporter.bail(id: ProblemID, token: AntlrToken, builder: ProblemReport.() -> Unit): Nothing =
    bail(id, token.location, token.length, builder)

internal inline fun ProblemReporter.bail(id: ProblemID, token: StitcherToken, builder: ProblemReport.() -> Unit): Nothing =
    bail(id, token.start, token.length, builder)

public inline fun <T : Any> runBailing(action: () -> T): Result<T> = try {
    Result.success(action())
} catch (bail: BailException) {
    throw bail
} catch (e: Exception) {
    Result.failure(e)
}

public inline fun <T : Any> tryBailing(action: () -> T): Result<T?> = try {
    Result.success(action())
} catch (_: BailException) {
    Result.success(null)
} catch (e: Exception) {
    Result.failure(e)
}