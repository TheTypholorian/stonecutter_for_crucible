package dev.kikugie.stitcher.issue

import java.nio.file.Path

@DslMarker
@Retention(AnnotationRetention.SOURCE)
private annotation class ReportDsl

@ReportDsl
public class ProblemReport(
    public val id: ProblemID,
    public val file: Path,
    public var location: ProblemLocation,
    public var length: Int,
) {
    public lateinit var label: String
    public var severity: ProblemSeverity = ProblemSeverity.ERROR
    public var details: String? = null
    public var solution: String? = null
    public var documentation: String? = null
    public var exception: Throwable? = null
}

@JvmInline @JvmExposeBoxed @OptIn(ExperimentalStdlibApi::class)
public value class ProblemID(public val id: String) {
    public fun create(child: String): ProblemID = ProblemID("$id:$child")

    public companion object {
        public val SCANNER_GROUP: ProblemID = ProblemID("scanner")
        public val LAYOUT_GROUP: ProblemID = ProblemID("layout")
        public val SYNTAX_GROUP: ProblemID = ProblemID("syntax")
    }
}

public enum class ProblemSeverity {
    INFO, WARN, ERROR
}