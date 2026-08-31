package dev.kikugie.stonecutter.build.task

import dev.kikugie.stitcher.document.issue.ProblemReport
import dev.kikugie.stitcher.document.issue.output.FullProblemFormatter
import dev.kikugie.stitcher.document.issue.output.ShortProblemFormatter
import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.problems.Problems
import org.gradle.api.provider.MapProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

@Suppress("UnstableApiUsage")
internal abstract class StonecutterErrorsService : BuildService<StonecutterErrorsService.Parameters>, OperationCompletionListener {
    private val printed: ConcurrentHashMap.KeySetView<ProblemReport, Boolean> = ConcurrentHashMap.newKeySet()
    private val buckets: ConcurrentHashMap<String, TaskBucket> = ConcurrentHashMap<String, TaskBucket>()

    @get:Inject
    protected abstract val problems: Problems

    private val gradleReporter: StonecutterProblemsReporter by lazy { StonecutterProblemsReporter(problems.reporter) }

    override fun onFinish(event: FinishEvent) {
        // no-op: this is only to make the service persist until the end of all tasks
    }

    fun report(task: String, report: ProblemReport) = with(bucket(task)) {
        gradleReporter.submit(report)
        reports.add(report)
        if (report.severity == ProblemReport.Severity.ERROR)
            errors.add(report)
        if (immediate && printed.add(report))
            Logging.getLogger(task.substringAfterLast(':')).log(report)
    }

    fun complete(task: String) = with(bucket(task)) {
        val logger = Logging.getLogger(task.substringAfterLast(':'))
        for (report in reports) if (printed.add(report))
            logger.log(report.level, report.accept(FullProblemFormatter))

        if (errors.isNotEmpty())
            errors.collect()
    }

    private fun bucket(task: String): TaskBucket = buckets.computeIfAbsent(task) {
        val path = task.substringBeforeLast(':')
        val immediate = parameters.active.getting(path).getOrElse(false)
        TaskBucket(immediate)
    }

    interface Parameters : BuildServiceParameters {
        val active: MapProperty<String, Boolean>
    }

    private data class TaskBucket(
        val immediate: Boolean,
        val reports: MutableCollection<ProblemReport> = ConcurrentLinkedQueue(),
        val errors: MutableCollection<ProblemReport> = ConcurrentLinkedQueue(),
    )

    companion object {
        const val NAME: String = "StonecutterTaskErrors"

        val ProblemReport.level
            get() = when (severity) {
                ProblemReport.Severity.INFO -> LogLevel.LIFECYCLE
                ProblemReport.Severity.WARN -> LogLevel.WARN
                ProblemReport.Severity.ERROR -> LogLevel.ERROR
            }

        fun Logger.log(report: ProblemReport) =
            log(report.level, report.accept(FullProblemFormatter))

        @Suppress("NOTHING_TO_INLINE")
        inline fun Iterable<ProblemReport>.collect(): Nothing {
            val joined = distinct().joinToString("\n") { "  " + it.accept(ShortProblemFormatter) }
            throw GradleException("File processing failed; see logs for more details.\n$joined")
        }
    }
}
