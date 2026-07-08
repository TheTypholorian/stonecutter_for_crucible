@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.document

import dev.kikugie.stitcher.issue.*
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.misc.Interval
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@DslMarker
@Retention(AnnotationRetention.SOURCE)
private annotation class DocumentDsl

@DocumentDsl
class Document(val path: Path, val source: CharStream, reporter: ProblemConsumer) : ProblemReporter {
    private val lines: DocumentLineIndex = DocumentLineIndex(source)
    private val problems: ProblemReporter = ProblemReporterImpl(this, reporter)

    override fun mark(): ProblemReporter.Marker = problems.mark()
    override fun create(id: ProblemID): ProblemReport = problems.create(id)
    override fun report(report: ProblemReport): Unit = problems.report(report)

    operator fun get(startIndex: Int, endIndex: Int): String =
        source.getText(Interval.of(startIndex, endIndex - 1))

    fun locate(index: Int): DocumentLocation =
        lines.locate(index)

    fun link(loc: DocumentLocation): String = when (loc.packed) {
        -1L -> "file://${path.absolutePathString()}"
        else -> "file://${path.absolutePathString()}:${loc.line}:${loc.column}"
    }
}
