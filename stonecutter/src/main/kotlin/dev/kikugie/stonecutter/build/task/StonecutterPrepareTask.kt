package dev.kikugie.stonecutter.build.task

import dev.kikugie.stitcher.issue.*
import dev.kikugie.stitcher.process
import dev.kikugie.stitcher.transformer.TransformParameters
import dev.kikugie.stonecutter.build.data.StonecutterBuildData
import dev.kikugie.stonecutter.controller.file.FileHandlerService
import dev.kikugie.stonecutter.controller.file.HandlerModel
import dev.kikugie.stonecutter.controller.tree.ParametersModel
import dev.kikugie.stonecutter.util.clearIfNotIncremental
import dev.kikugie.stonecutter.util.overwriteText
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.file.RegularFileProperty
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
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.readText

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

    @get:ServiceReference(StonecutterErrorsService.NAME)
    internal abstract val errors: Property<StonecutterErrorsService>

    @TaskAction
    internal fun run(inputs: InputChanges) {
        inputs.clearIfNotIncremental(destination.asFile.get())
        val model = ParametersModel(params.get())

        with(executor.noIsolation()) {
            for (change in inputs.getFileChanges(source))
                if (change.fileType != FileType.DIRECTORY)
                    processFile(change, model)
            await()
        }
        errors.get().complete(path)
    }

    private val File.cacheFile: File
        get() = destination.asFile.get().resolve(relativeTo(root.get()))

    private fun WorkQueue.processFile(change: FileChange, params: ParametersModel): Unit = submit(PrepareAction::class) {
        source.set(change.file)
        output.set(change.file.cacheFile)
        model.set(params)

        handlers.set(this@StonecutterPrepareTask.handlers)
        reporter.set(this@StonecutterPrepareTask.errors)
        task.set(this@StonecutterPrepareTask.path)
    }
}

private interface PrepareAction : WorkAction<PrepareAction.Parameters> {
    private val source: Path get() = parameters.source.asFile.get().toPath()
    private val output: Path get() = parameters.output.asFile.get().toPath()

    override fun execute() {
        val reporter: ProblemConsumer = { parameters.reporter.get().report(parameters.task.get(), it) }
        val transform = parameters.model.get().forFile(source, parameters.handlers.get().parameters.handlers.get())

        if (transform == null) reporter.invoke(ProblemReport(ProblemID("invalid-extension"), ProblemLocation(source)).apply {
            label = "No file handler registered for ${source.extension}"
            severity = ProblemSeverity.WARN
        })
        if (!source.exists() || transform == null) { output.deleteIfExists(); return }
        val contents = source.readText()
        val modified = try {
            process(source, contents, transform, reporter)
        } catch (_: BailException) {
            output.deleteIfExists()
            return // Will be rethrown as GradleException
        }
        if (contents == modified) output.deleteIfExists() else output.overwriteText(modified, true)
    }

    interface Parameters : WorkParameters {
        val source: RegularFileProperty
        val output: RegularFileProperty
        val model: Property<ParametersModel>

        val handlers: Property<FileHandlerService>
        val reporter: Property<StonecutterErrorsService>
        val task: Property<String>
    }
}

internal fun ParametersModel.forFile(file: Path, handlers: Map<String, HandlerModel>): TransformParameters? {
    val handler = handlers[file.extension.lowercase()] ?: return null
    return TransformParameters(handler.scanner, handler.commenter, handler.uncommenter, handler.swapper, swaps, constants, dependencies, replacements)
}
