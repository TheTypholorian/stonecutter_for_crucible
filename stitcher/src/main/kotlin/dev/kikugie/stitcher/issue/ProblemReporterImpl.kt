package dev.kikugie.stitcher.issue

import dev.kikugie.stitcher.issue.lines.LineIndex
import java.nio.file.Path

class ProblemReporterImpl(override val file: Path, val index: LineIndex, val consumer: (ProblemReport) -> Unit) : FileProblemReporter {
    constructor(file: Path, contents: String, consumer: (ProblemReport) -> Unit) : this(file, LineIndex(contents), consumer)
    private var counter: Int = 0

    override fun mark(): ProblemMarker = ProblemMarkerImpl(counter)

    override fun locate(index: Int): ProblemLocation =
        this.index.locate(file, index)

    override fun accept(report: ProblemReport) {
        counter++
        consumer(report)
    }

    private inner class ProblemMarkerImpl(val checkpoint: Int) : ProblemMarker {
        override val isDirty: Boolean get() = checkpoint < this@ProblemReporterImpl.counter
    }
}