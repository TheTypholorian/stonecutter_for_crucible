package dev.kikugie.stonecutter.build.task

import dev.kikugie.stitcher.issue.FullProblemFormatter
import dev.kikugie.stitcher.issue.ProblemConsumer
import dev.kikugie.stitcher.issue.ProblemReport
import dev.kikugie.stitcher.issue.ProblemSeverity
import dev.kikugie.stitcher.issue.ShortProblemFormatter
import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.provider.MapProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

internal abstract class StonecutterErrorsService : BuildService<StonecutterErrorsService.Parameters>, OperationCompletionListener {
    private val allRrinted = ConcurrentHashMap.newKeySet<ProblemReport>()

    override fun onFinish(event: FinishEvent) {
        // no-op: this is only to make the service persist until the end of all tasks
    }

    interface Parameters : BuildServiceParameters {
        val active: MapProperty<String, Boolean>
    }

    inner class GradleProblemConsumer(val task: String, val logger: Logger): ProblemConsumer, Serializable {
        val immediate: Boolean = parameters.active.getting(task.substringBeforeLast(':')).getOrElse(false)
        private val myReports = ConcurrentLinkedQueue<ProblemReport>()
        private val myErrors = ConcurrentLinkedQueue<ProblemReport>()

        override fun invoke(report: ProblemReport) {
            myReports += report
            if (report.severity == ProblemSeverity.ERROR)
                myErrors += report
            if (immediate && allRrinted.add(report))
                print(report)
        }

        fun complete() {
            for (report in myReports) if (allRrinted.add(report))
                print(report)
            if (myErrors.isNotEmpty()) {
                val joined = myErrors.distinct().joinToString("\n") { "  " + it.accept(ShortProblemFormatter) }
                throw GradleException("File processing failed; see logs for more details.\n$joined")
            }
        }

        private fun print(report: ProblemReport) {
            logger.log(report.level, report.accept(FullProblemFormatter))
        }
    }

    companion object {
        const val NAME: String = "StonecutterTaskErrors"
    }
}

private val ProblemReport.level get() = when(severity) {
    ProblemSeverity.INFO -> LogLevel.LIFECYCLE
    ProblemSeverity.WARN -> LogLevel.WARN
    ProblemSeverity.ERROR -> LogLevel.ERROR
}
