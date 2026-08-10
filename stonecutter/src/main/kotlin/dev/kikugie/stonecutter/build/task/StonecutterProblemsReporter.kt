package dev.kikugie.stonecutter.build.task

import dev.kikugie.stitcher.document.DocumentReference
import dev.kikugie.stitcher.document.issue.BailException
import dev.kikugie.stitcher.document.issue.ProblemReport
import dev.kikugie.stitcher.document.issue.ProblemReport.Severity.*
import dev.kikugie.stitcher.util.length
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemReporter
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.Problems
import org.gradle.api.problems.Severity

@JvmInline @Suppress("UnstableApiUsage")
internal value class StonecutterProblemsReporter(val reporter: ProblemReporter) {
    fun submit(report: ProblemReport) = when (report.severity) {
        INFO -> {} // Gradle deprecated "advice" reports, bitch
        WARN -> reporter.report(report.id()) { apply(report) }
        ERROR -> try { // It also made severity automatic, requiring throws for errors, boo!
            reporter.throwing(FAKE_BAIL_EXCEPTION, report.id()) { apply(report) }
        } catch (_: BailException) {
        }
    }

    private fun ProblemReport.id(): ProblemId {
        var group: ProblemGroup = STONECUTTER_GROUP
        val entries: List<String> = id.id.split(':')

        for (it in entries.dropLast(1))
            group = group.child(it)
        return group.end(entries.last())
    }

    private fun ProblemSpec.apply(report: ProblemReport) {
        contextualLabel(report.label)
        report.info?.let { details(it) }
        report.hint?.let { solution(it) }
        report.docs?.let { documentedAt(it) }
        report.error?.let { withException(it) }

        val path = report.at.document.file.toString()
        val location = report.at.location
        if (!location.isKnown) fileLocation(path)
        else lineInFileLocation(path, location.line, location.column, report.at.length)
    }

    private fun ProblemGroup.child(id: String, display: String = id): ProblemGroup =
        ProblemGroup.create(id, display, this)

    private fun ProblemGroup.end(id: String, display: String = id): ProblemId =
        ProblemId.create(id, display, this)

    private companion object {
        val STONECUTTER_GROUP: ProblemGroup = ProblemGroup.create("stonecutter", "Stonecutter")
        val FAKE_BAIL_EXCEPTION: BailException by lazy { BailException().apply { stackTrace = emptyArray() } }
    }
}
