package dev.kikugie.stonecutter.build.task

import dev.kikugie.stitcher.Stitcher
import dev.kikugie.stitcher.StitcherParameters
import dev.kikugie.stitcher.document.SourceDocument
import dev.kikugie.stitcher.document.io.LineNormalizingReader
import dev.kikugie.stitcher.document.io.LineNormalizingWriter
import dev.kikugie.stitcher.document.issue.*
import dev.kikugie.stitcher.document.reference
import dev.kikugie.stitcher.token.BlockToken
import dev.kikugie.stitcher.token.appendTo
import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.build.data.StonecutterBuildData
import dev.kikugie.stonecutter.controller.file.FileHandlerService
import dev.kikugie.stonecutter.controller.file.HandlerModel
import dev.kikugie.stonecutter.controller.tree.*
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import dev.kikugie.stonecutter.model.process.ProcessParameters
import dev.kikugie.stonecutter.util.clearIfNotIncremental
import org.gradle.api.DefaultTask
import org.gradle.api.file.*
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.submit
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
    @get:Internal
    public abstract val processable: ConfigurableFileCollection

    /**The version-specific files overriding shared sources, excluded from the output.*/
    @get:Internal
    public abstract val local: ConfigurableFileCollection

    @get:Input
    internal val processablePaths: Provider<Set<String>>
        get() = processable.elements.map { it.relativePaths(root.get()) }

    @get:Input
    internal val localPaths: Provider<Set<String>>
        get() = local.elements.map { it.relativePaths(localRoot.get()) }

    @get:Input
    internal val handlerFingerprints: Provider<Map<String, String>>
        get() = processablePaths.zip(handlers) { paths, service ->
            paths.mapTo(sortedSetOf()) { it.substringAfterLast('/').substringAfterLast('.', "").lowercase() }
                .mapNotNull { ext -> service.handlers[ext]?.let { ext to it.fingerprint() } }.toMap()
        }

    /**The base directory of the [local] overrides used for resolving relative paths.*/
    @get:Internal
    public abstract val localRoot: Property<File>

    /**The output directory at `build/generated/stonecutter/`.*/
    @get:OutputDirectory
    public abstract val destination: DirectoryProperty

    /**The line separator written between lines of processed files.*/
    @get:Input
    internal abstract val lineEnding: Property<String>

    @get:Input
    internal abstract val flagRegistry: Property<String>

    @get:Inject
    internal abstract val executor: WorkerExecutor

    @get:ServiceReference(FileHandlerService.NAME)
    internal abstract val handlers: Property<FileHandlerService>

    @get:ServiceReference(StonecutterErrorsService.NAME)
    internal abstract val errors: Property<StonecutterErrorsService>

    init {
        lineEnding.convention(System.lineSeparator())
        flagRegistry.convention(ConfigurationRegistry().toJsonString())
    }

    @TaskAction
    internal fun run(inputs: InputChanges) {
        inputs.clearIfNotIncremental(destination.asFile.get())
        val data = params.get()
        val model = data.toParameters()
        val scopes: Map<Int, Set<File>> = model.replacements.resolveFiles(source.asFileTree)
        val processable: Set<String> = processablePaths.get()

        with(executor.noIsolation()) {
            processFileChanges(inputs, model, scopes, processable)
            await()
        }
        errors.get().complete(path)
    }

    private fun WorkQueue.processFileChanges(inputs: InputChanges, model: ProcessParameters, scopes: Map<Int, Set<File>>, processable: Set<String>) {
        for (change in inputs.getFileChanges(source)) when {
            change.fileType == FileType.DIRECTORY -> continue
            change.changeType == ChangeType.REMOVED -> change.file.generatedFile.delete()
            else -> processFile(
                change, model.restrictedTo(change.file, scopes),
                change.file.relativeTo(root.get()).invariantSeparatorsPath in processable
            )
        }
    }

    private val File.generatedFile: File
        get() = destination.asFile.get().resolve(relativeTo(root.get()))

    private val File.overrideFile: File
        get() = localRoot.get().resolve(relativeTo(root.get()))

    private fun WorkQueue.processFile(change: FileChange, params: ProcessParameters, process: Boolean): Unit =
        submit(GenerateAction::class) {
            source.set(change.file)
            output.set(change.file.generatedFile)
            override.set(change.file.overrideFile)
            model.set(params)
            flags.set(flagRegistry)
            transform.set(process)
            lineEnding.set(this@StonecutterGenerateTask.lineEnding)

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
        val model: Property<ProcessParameters>
        val flags: Property<String>
        val transform: Property<Boolean>
        val lineEnding: Property<String>

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

        if (!parameters.transform.get()) return copyDirectly()
        val parameters = createTransformParameters() ?: return copyDirectly()
        val document = createSourceDocument() ?: return
        val processed = tryProcessFile(document, parameters) ?: return copyDirectly()

        val separator = this.parameters.lineEnding.get()
        output.createParentDirectories()
        output.bufferedWriter(Charsets.UTF_8).use { raw ->
            LineNormalizingWriter(raw, separator).use { processed.appendTo(it) }
        }
        copyMetadata()
    }

    private fun copyDirectly() {
        output.createParentDirectories()
        source.copyTo(output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
    }

    private fun copyMetadata() {
        if (source.fileSystem.supportedFileAttributeViews().contains("posix"))
            output.setPosixFilePermissions(source.getPosixFilePermissions())
        output.setLastModifiedTime(source.getLastModifiedTime())
    }

    private fun handleNoFile() {
        createDummyDocument().reference().report(GENERATE_TASK + "no-source") {
            label = "No such file"
        }
        output.deleteIfExists()
    }

    private fun createProblemReporter(): ProblemReporter {
        val reporter: StonecutterErrorsService = parameters.reporter.get()
        val task: String = parameters.task.get()
        return DefaultProblemReporter({ reporter.report(task, it) })
    }

    private fun createTransformParameters(): StitcherParameters? {
        val handlers: Map<String, HandlerModel> = parameters.handlers.get().handlers
        val flags: ConfigurationRegistry = restoreFromJson(parameters.flags.get())
        return parameters.model.get().forFile(source, handlers, flags)
    }

    private fun createSourceDocument(): SourceDocument? {
        val source = source
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = tryReadFile(source, decoder) ?: return null
        return SourceDocument(source, text, createProblemReporter())
    }

    private fun createDummyDocument(): SourceDocument =
        SourceDocument(source, "", createProblemReporter())

    fun tryReadFile(source: Path, decoder: CharsetDecoder): String? = try {
        LineNormalizingReader(InputStreamReader(source.inputStream(), decoder).buffered()).use { it.readText() }
    } catch (e: CharacterCodingException) {
        createDummyDocument().reference().report(GENERATE_TASK + "invalid-encoding") {
            label = "Invalid file encoding"
            info = "File is not valid UTF-8 and can't be processed."
            error = e
        }
        null
    }

    private fun tryProcessFile(document: SourceDocument, parameters: StitcherParameters): BlockToken.Root? = try {
        Stitcher.process(document, parameters)
    } catch (_: BailException) {
        null
    } catch (e: Exception) {
        document.reference(-1).report(GENERATE_TASK + "uncaught-failure") {
            label = "Uncaught exception"
            info = "File processing failed because of an unexpected error (╭ರ_•́)."
            hint = """
                Please report this to https://codeberg.org/stonecutter/stonecutter/issues
                with the problematic file and stacktrace attached.
            """.trimIndent()
            error = e
        }
        null
    }
}

private fun Set<FileSystemLocation>.relativePaths(root: File): Set<String> =
    mapTo(sortedSetOf()) { it.asFile.relativeTo(root).invariantSeparatorsPath }
