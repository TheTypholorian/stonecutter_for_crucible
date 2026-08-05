package dev.kikugie.stonecutter.build.task

import dev.kikugie.stitcher.Stitcher
import dev.kikugie.stitcher.StitcherParameters
import dev.kikugie.stitcher.document.SourceDocument
import dev.kikugie.stitcher.document.io.LineNormalizingReader
import dev.kikugie.stitcher.document.io.LineNormalizingWriter
import dev.kikugie.stitcher.document.issue.BailException
import dev.kikugie.stitcher.document.issue.ProblemID
import dev.kikugie.stitcher.document.issue.output.ProblemConsumer
import dev.kikugie.stitcher.document.issue.report
import dev.kikugie.stitcher.document.reference
import dev.kikugie.stitcher.token.appendTo
import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.build.data.StonecutterBuildData
import dev.kikugie.stonecutter.controller.file.FileHandlerService
import dev.kikugie.stonecutter.controller.file.HandlerModel
import dev.kikugie.stonecutter.controller.tree.ParametersModel
import dev.kikugie.stonecutter.util.clearIfNotIncremental
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
import kotlin.io.path.*

private val GENERATE_TASK: ProblemID = ProblemID("file-processing")

/**
 * Task responsible for generating processed sources for a given [SourceSet].
 *
 * Files matching the configured [processable] filters are run through [Stitcher];
 * every other shared file is copied verbatim. Files with a matching [local]
 * version override are skipped, since those are served directly from the version
 * source directory.
 *
 * This task should be considered an internal implementation
 * and only be configured directly in rare edge cases.
 */
@CacheableTask @StonecutterInternalAPI
public abstract class StonecutterGenerateTask : DefaultTask() {
    @get:Nested
    internal abstract val params: Property<StonecutterBuildData>

    /**The task base directory at `src/` used for resolving relative paths.*/
    @get:Input
    public abstract val root: Property<File>

    /**All shared source set files, processed or copied depending on [processable].*/
    @get:InputFiles @get:Incremental
    @get:IgnoreEmptyDirectories @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val source: ConfigurableFileCollection

    /**The subset of [source] files that should be run through the preprocessor.*/
    @get:InputFiles @get:IgnoreEmptyDirectories @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val processable: ConfigurableFileCollection

    /**The version-specific files overriding shared sources, excluded from the output.*/
    @get:InputFiles @get:IgnoreEmptyDirectories @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val local: ConfigurableFileCollection

    /**The base directory of the [local] overrides used for resolving relative paths.*/
    @get:Internal
    public abstract val localRoot: Property<File>

    /**The output directory at `build/generated/stonecutter/`.*/
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
        val processable: Set<File> = processable.files

        with(executor.noIsolation()) {
            for (change in inputs.getFileChanges(source))
                if (change.fileType != FileType.DIRECTORY)
                    processFile(change, model, change.file in processable)
            await()
        }
        errors.get().complete(path)
    }

    private val File.generatedFile: File
        get() = destination.asFile.get().resolve(relativeTo(root.get()))

    private val File.overrideFile: File
        get() = localRoot.get().resolve(relativeTo(root.get()))

    private fun WorkQueue.processFile(change: FileChange, params: ParametersModel, process: Boolean): Unit =
        submit(GenerateAction::class) {
            source.set(change.file)
            output.set(change.file.generatedFile)
            override.set(change.file.overrideFile)
            model.set(params)
            transform.set(process)

            handlers.set(this@StonecutterGenerateTask.handlers)
            reporter.set(this@StonecutterGenerateTask.errors)
            task.set(this@StonecutterGenerateTask.path)
        }
}

private interface GenerateAction : WorkAction<GenerateAction.Parameters> {
    interface Parameters : WorkParameters {
        val source: RegularFileProperty
        val output: RegularFileProperty
        val override: RegularFileProperty
        val model: Property<ParametersModel>
        val transform: Property<Boolean>

        val handlers: Property<FileHandlerService>
        val reporter: Property<StonecutterErrorsService>
        val task: Property<String>
    }

    private val source: Path get() = parameters.source.asFile.get().toPath()
    private val output: Path get() = parameters.output.asFile.get().toPath()
    private val override: Path get() = parameters.override.asFile.get().toPath()

    override fun execute() {
        if (source.notExists()) return handleNoFile()
        if (override.exists()) return output.deleteIfExists().let { }

        val parameters = if (parameters.transform.get()) createTransformParameters() else null
        if (parameters == null) return copyDirectly()

        val document = createSourceDocument()
        val processed = try {
            Stitcher.process(document, parameters)
        } catch (_: BailException) {
            // Processing failed and was reported; fall back to the raw file so the tree stays complete.
            return copyDirectly()
        }

        output.createParentDirectories()
        output.bufferedWriter(Charsets.UTF_8).use { raw ->
            LineNormalizingWriter(raw).use { processed.appendTo(it) }
        }
    }

    private fun copyDirectly() {
        output.createParentDirectories()
        source.copyTo(output, overwrite = true)
    }

    private fun handleNoFile() {
        createDummyDocument().reference().report(GENERATE_TASK + "no-source") {
            label = "No such file"
        }
        output.deleteIfExists()
    }

    private fun createProblemConsumer(): ProblemConsumer {
        val reporter: StonecutterErrorsService = parameters.reporter.get()
        val task: String = parameters.task.get()
        return ProblemConsumer { reporter.report(task, it) }
    }

    private fun createTransformParameters(): StitcherParameters? {
        val handlers: Map<String, HandlerModel> = parameters.handlers.get().parameters.handlers.get()
        return parameters.model.get().forFile(source, handlers)
    }

    private fun createSourceDocument(): SourceDocument {
        val source = source
        val text = LineNormalizingReader(source.bufferedReader()).use { it.readText() }
        return SourceDocument(source, text, createProblemConsumer())
    }

    private fun createDummyDocument(): SourceDocument =
        SourceDocument(source, "", createProblemConsumer())
}
