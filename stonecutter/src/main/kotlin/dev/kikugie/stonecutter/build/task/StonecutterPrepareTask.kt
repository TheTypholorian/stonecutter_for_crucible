package dev.kikugie.stonecutter.build.task

import dev.kikugie.antlr.util.length
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.io.LineNormalizingReader
import dev.kikugie.stitcher.document.io.LineNormalizingWriter
import dev.kikugie.stitcher.issue.*
import dev.kikugie.stitcher.process
import dev.kikugie.stitcher.transformer.TransformParameters
import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.build.data.StonecutterBuildData
import dev.kikugie.stonecutter.controller.file.FileHandlerService
import dev.kikugie.stonecutter.controller.file.HandlerModel
import dev.kikugie.stonecutter.controller.tree.ParametersModel
import dev.kikugie.stonecutter.util.clearIfNotIncremental
import dev.kikugie.stonecutter.util.overwriteText
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.misc.Interval
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
import java.io.StringWriter
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.*

private val PREPARE_TASK: ProblemID = ProblemID("file-processing")

/**
 * Task responsible for preprocessing files for a given [SourceSet].
 *
 * This task should be considered an internal implementation
 * and only be configured directly in rare edge cases.
 */
@CacheableTask @StonecutterInternalAPI
public abstract class StonecutterPrepareTask : DefaultTask() {
    @get:Nested
    internal abstract val params: Property<StonecutterBuildData>

    /**The task base directory at `src/` used for resolving relative paths.*/
    @get:Input
    public abstract val root: Property<File>

    /**The filtered source set files.*/
    @get:InputFiles @get:Incremental
    @get:IgnoreEmptyDirectories @get:PathSensitive(PathSensitivity.RELATIVE)
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
    interface Parameters : WorkParameters {
        val source: RegularFileProperty
        val output: RegularFileProperty
        val model: Property<ParametersModel>

        val handlers: Property<FileHandlerService>
        val reporter: Property<StonecutterErrorsService>
        val task: Property<String>
    }

    private val source: Path get() = parameters.source.asFile.get().toPath()
    private val output: Path get() = parameters.output.asFile.get().toPath()

    override fun execute() {
        if (source.notExists()) return handleNoFile()

        val parameters = createTransformParameters() ?: return handleNoParams()
        val document = createSourceDocument()

        val modified = try { process(document, parameters) } catch (_: BailException) { return }
        if (!isModified(document, modified)) return output.deleteIfExists().let { }

        val writer = StringWriter()
        LineNormalizingWriter(writer).write(modified)

        output.overwriteText(writer.toString(), true)
    }

    private fun handleNoFile(): Unit = createDummyDocument().report(PREPARE_TASK + "no-source") {
        label = "No such file"
        output.deleteIfExists()
    }

    private fun handleNoParams(): Unit = createDummyDocument().report(PREPARE_TASK + "invalid-extension") {
        label = "No file handler registered for ${source.extension}"
        sev = ProblemSeverity.WARN
        output.deleteIfExists()
    }

    private fun createProblemConsumer(): ProblemConsumer {
        val reporter: StonecutterErrorsService = parameters.reporter.get()
        val task: String = parameters.task.get()
        return ProblemConsumer { reporter.report(task, it) }
    }

    private fun createTransformParameters(): TransformParameters? {
        val handlers: Map<String, HandlerModel> = parameters.handlers.get().parameters.handlers.get()
        return parameters.model.get().forFile(source, handlers)
    }

    private fun createSourceDocument(): Document {
        val source = source
        val stream = CharStreams.fromReader(LineNormalizingReader(source.bufferedReader()), source.absolutePathString())
        return Document(source, stream, createProblemConsumer())
    }

    private fun createDummyDocument(): Document =
        Document(source, CharStreams.fromString(""), createProblemConsumer())

    private fun isModified(document: Document, new: String): Boolean {
        val size = document.source.length
        if (size != new.length) return true

        val original = document.source.getText(Interval.of(0, size - 1))
        return original != new
    }
}
