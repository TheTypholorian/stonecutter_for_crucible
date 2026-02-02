package dev.kikugie.stonecutter.build.task

import dev.kikugie.stitcher.issue.BailException
import dev.kikugie.stitcher.issue.ProblemID
import dev.kikugie.stitcher.issue.ProblemLocation
import dev.kikugie.stitcher.issue.ProblemReport
import dev.kikugie.stitcher.issue.ProblemSeverity
import dev.kikugie.stitcher.process
import dev.kikugie.stitcher.transformer.TransformParameters
import dev.kikugie.stonecutter.build.data.StonecutterBuildData
import dev.kikugie.stonecutter.controller.file.FileHandlerService
import dev.kikugie.stonecutter.controller.file.HandlerModel
import dev.kikugie.stonecutter.controller.tree.ParametersModel
import dev.kikugie.stonecutter.util.clearIfNotIncremental
import dev.kikugie.stonecutter.util.execute
import dev.kikugie.stonecutter.util.overwriteText
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.submit
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.*

/**The task responsible for processing Stonecutter comments in files.*/
public abstract class StonecutterPrepareTask : DefaultTask() {
    @get:Nested
    internal abstract val params: Property<StonecutterBuildData>

    /**The task base directory at `src/` used for resolving relative paths.*/
    @get:Input
    public abstract val root: Property<File>

    /**The filtered source set files.*/
    @get:InputFiles @get:Incremental @get:IgnoreEmptyDirectories
    public abstract val source: ConfigurableFileCollection

    /**The output directory at `build/stonecutter-cache/`.*/
    @get:OutputDirectory
    public abstract val destination: DirectoryProperty

    @get:Inject
    internal abstract val executor: WorkerExecutor

    @get:ServiceReference(FileHandlerService.NAME)
    internal abstract val handlers: Property<FileHandlerService>

    @get:ServiceReference(TaskErrorsService.NAME)
    internal abstract val errors: Property<TaskErrorsService>

    @TaskAction
    internal fun run(inputs: InputChanges) {
        inputs.clearIfNotIncremental(destination.asFile.get())
        val model = ParametersModel(params.get())

        executor.execute {
            for (change in inputs.getFileChanges(source))
                if (change.fileType != FileType.DIRECTORY)
                    it.processFile(change, model)
        }
    }

    private val File.cacheFile: File
        get() = destination.asFile.get().resolve(relativeTo(root.get()))

    private fun WorkQueue.processFile(change: FileChange, params: ParametersModel): Unit = submit(PrepareAction::class) {
        source.set(change.file)
        output.set(change.file.cacheFile)
        model.set(params)

        handlers.set(this@StonecutterPrepareTask.handlers)
        errors.set(this@StonecutterPrepareTask.errors)
    }
}

private interface PrepareAction : WorkAction<PrepareAction.Parameters> {
    private val source: Path get() = parameters.source.asFile.get().toPath()
    private val output: Path get() = parameters.output.asFile.get().toPath()

    override fun execute() {
        val reporter = GradleProblemReporter(Logging.getLogger("StonecutterPrepareTask"), parameters.errors.get()::isNew)
        val transform = parameters.model.get().forFile(source, parameters.handlers.get().parameters.handlers.get())

        if (transform == null) reporter.invoke(ProblemReport(ProblemID("invalid-extension"), ProblemLocation(source)).apply {
            label = "No file handler registered for ${source.extension}"
            severity = ProblemSeverity.WARN
        })
        if (!source.exists() || transform == null) { output.deleteIfExists(); return }
        val contents = source.readText()
        val modified = reporter.run { process(source, contents, transform, reporter) }
        if (contents == modified) output.deleteIfExists() else output.overwriteText(modified, true)
    }

    interface Parameters : WorkParameters {
        val source: RegularFileProperty
        val output: RegularFileProperty
        val model: Property<ParametersModel>

        val handlers: Property<FileHandlerService>
        val errors: Property<TaskErrorsService>
    }
}

internal class GradleProblemReporter(val logger: Logger, val checker: (ProblemReport) -> Boolean) : (ProblemReport) -> Unit {
    private val errors: MutableList<ProblemReport> = mutableListOf()

    override fun invoke(report: ProblemReport) {
        errors += report
        if (checker(report)) logger.log(report.level(), report.formatFull())
    }

    inline fun <T> run(action: () -> T) = try {
        action()
    } catch (_: BailException) {
        val formatted = errors.joinToString("\n") { it.formatShort() }.prependIndent("  ")
        throw GradleException("File processing failed; see log for more info.\n$formatted")
    }

    // FIXME: this code is shit, but works as a prototype
    private fun ProblemReport.formatFull(): String = buildString {
        val file = location?.run { "$file:$line:$column" } ?: ""
        val sev = when(severity) {
            ProblemSeverity.INFO -> 'i'
            ProblemSeverity.WARN -> 'w'
            ProblemSeverity.ERROR -> 'e'
        }
        appendLine("$sev: $file ${id.id}")
        appendLine("> $label")
        for (line in details?.lineSequence().orEmpty())
            appendLine("|  $line")
        solution?.let { appendLine("Hint: $it") }
        documentation?.let { appendLine("Docs: $it") }
        exception?.let { appendLine("Error: ${it.message}\n${it.stackTraceToString()}") }
    }

    private fun ProblemReport.level() = when(severity) {
        ProblemSeverity.INFO -> LogLevel.LIFECYCLE
        ProblemSeverity.WARN -> LogLevel.WARN
        ProblemSeverity.ERROR -> LogLevel.ERROR
    }

    private fun ProblemReport.formatShort() = buildString {
        append(location?.run { "$file:$line:$column" } ?: "")
        append(" $label")
    }
}

internal fun ParametersModel.forFile(file: Path, handlers: Map<String, HandlerModel>): TransformParameters? {
    val handler = handlers[file.extension.lowercase()] ?: return null
    return TransformParameters(handler.scanner, handler.commenter, handler.uncommenter, handler.swapper, swaps, constants, dependencies, replacements)
}