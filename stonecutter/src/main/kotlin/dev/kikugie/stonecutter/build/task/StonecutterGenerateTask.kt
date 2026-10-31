package dev.kikugie.stonecutter.build.task

import dev.kikugie.commons.collections.associateWithNotNull
import dev.kikugie.stitcher.Stitcher
import dev.kikugie.stitcher.StitcherParameters
import dev.kikugie.stitcher.document.SourceDocument
import dev.kikugie.stitcher.document.io.LineNormalizingReader
import dev.kikugie.stitcher.document.io.LineNormalizingWriter
import dev.kikugie.stitcher.document.issue.*
import dev.kikugie.stitcher.document.reference
import dev.kikugie.stitcher.token.BlockToken
import dev.kikugie.stitcher.token.appendTo
import dev.kikugie.stitcher.token.type.RootScopeState
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
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.submit
import org.gradle.work.ChangeType
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
 * This task should be considered an internal implementation
 * and only be configured directly in rare edge cases.
 */
@CacheableTask @StonecutterInternalAPI
public abstract class StonecutterGenerateTask : DefaultTask() {
    /** Parameters used by Stitcher processor. */
    @get:Nested
    internal abstract val params: Property<StonecutterBuildData>

    /** Maps directory names in `build/generated/stonecutter/{source set}/` to the actual directory being processed. */
    @get:Internal
    public abstract val sourceRoots: MapProperty<String, File>

    /** Maps directory names in `build/generated/stonecutter/{source set}/` to its overrides directory. */
    @get:Internal
    public abstract val overrideRoots: MapProperty<String, File>

    /**All shared source set files, processed or copied depending on [processable].*/
    @get:[InputFiles Incremental IgnoreEmptyDirectories PathSensitive(PathSensitivity.RELATIVE)]
    public abstract val sources: ConfigurableFileCollection

    /** Filtered subset of [sourceRoots] with applied [FileHandlerService] specs. */
    @get:Internal
    public abstract val processable: ConfigurableFileCollection

    /** Filtered subset of [overrideRoots] with applied [SourceSet] filters. */
    @get:Internal
    public abstract val overrides: ConfigurableFileCollection

    @get:Input
    internal val sourcePaths: Provider<Set<String>>
        get() = sources.elements.map { it.rootPaths(sourceRoots.get()) }

    @get:Input
    internal val processablePaths: Provider<Set<String>>
        get() = processable.elements.map { it.rootPaths(sourceRoots.get()) }

    @get:Input
    internal val overridePaths: Provider<Set<String>>
        get() = overrides.elements.map { it.rootPaths(overrideRoots.get()) }

    @get:Input
    internal val handlerFingerprints: Provider<Map<String, String>>
        get() = processablePaths.zip(handlers) { paths, service ->
            paths.mapTo(sortedSetOf()) { it.substringAfterLast('/').substringAfterLast('.', "").lowercase() }
                .associateWithNotNull { service.handlers[it]?.fingerprint() }
        }

    /** The line separator written between lines of processed files. When empty, each file keeps its own. */
    @get:Input
    internal abstract val lineEnding: Property<String>

    @get:Input
    internal abstract val flagRegistry: Property<String>

    @get:Inject
    internal abstract val executor: WorkerExecutor

    /**The output directory at `build/generated/stonecutter/`.*/
    @get:OutputDirectory
    public abstract val destination: DirectoryProperty

    @get:ServiceReference(FileHandlerService.NAME)
    internal abstract val handlers: Property<FileHandlerService>

    @get:ServiceReference(StonecutterErrorsService.NAME)
    internal abstract val errors: Property<StonecutterErrorsService>

    init {
        lineEnding.convention("")
        flagRegistry.convention(ConfigurationRegistry().toJsonString())
        sourceRoots.finalizeValueOnRead()
        overrideRoots.finalizeValueOnRead()
    }

    @TaskAction
    internal fun run(inputs: InputChanges) {
        inputs.clearIfNotIncremental(destination.asFile.get())
        val data = params.get()
        val model = data.toParameters()
        val scopes: Map<Int, Set<File>> = model.replacements.resolveFiles(sources.asFileTree)
        val states: Map<File, RootScopeState> = model.files.resolveStates(sources.asFileTree)
        val processable: Set<String> = processablePaths.get()

        with(executor.noIsolation()) {
            processFileChanges(inputs, model, scopes, states, processable)
            await()
        }
        errors.get().complete(path)
    }

    private fun WorkQueue.processFileChanges(
        inputs: InputChanges,
        model: ProcessParameters,
        scopes: Map<Int, Set<File>>,
        states: Map<File, RootScopeState>,
        processable: Set<String>
    ) {
        val roots: Map<String, File> = sourceRoots.get()
        val overrides: Map<String, File> = overrideRoots.get()
        val destination: File = destination.asFile.get()

        for (change in inputs.getFileChanges(sources)) if (change.fileType != FileType.DIRECTORY) {
            val (root, relative) = roots.locate(change.file)
                ?: continue
            val output: File = destination.resolve(root).resolve(relative)

            if (change.changeType == ChangeType.REMOVED) output.delete() else processFile(
                change.file,
                output,
                overrides.getValue(root).resolve(relative),
                model.restrictedTo(change.file, scopes),
                states[change.file] ?: RootScopeState.ANY,
                "$root/$relative" in processable
            )
        }
    }

    private fun WorkQueue.processFile(file: File, generated: File, overrideFile: File, params: ProcessParameters, state: RootScopeState, process: Boolean): Unit =
        submit(GenerateAction::class) {
            source.set(file)
            output.set(generated)
            override.set(overrideFile)
            model.set(params)
            root.set(state)
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
        val root: Property<RootScopeState>
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
        val (document, detected) = createSourceDocument() ?: return
        val processed = tryProcessFile(document, parameters) ?: return copyDirectly()

        val separator = this.parameters.lineEnding.get()
            .ifEmpty { detected ?: System.lineSeparator() }
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
        return parameters.model.get().forFile(source, handlers, flags, parameters.root.get())
    }

    private fun createSourceDocument(): Pair<SourceDocument, String?>? {
        val source = source
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val (text, separator) = tryReadFile(source, decoder) ?: return null
        return SourceDocument(source, text, createProblemReporter()) to separator
    }

    private fun createDummyDocument(): SourceDocument =
        SourceDocument(source, "", createProblemReporter())

    fun tryReadFile(source: Path, decoder: CharsetDecoder): Pair<String, String?>? = try {
        LineNormalizingReader(InputStreamReader(source.inputStream(), decoder).buffered()).use { it.readText() to it.separator }
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

private fun Map<String, File>.locate(file: File): Pair<String, String>? = entries
    .filter { file.startsWith(it.value) }
    .maxByOrNull { it.value.path.length }
    ?.let { (root, dir) -> root to file.relativeTo(dir).invariantSeparatorsPath }

private fun Set<FileSystemLocation>.rootPaths(roots: Map<String, File>): Set<String> = mapNotNullTo(sortedSetOf()) { location ->
    val (root, relative) = roots.locate(location.asFile) ?: return@mapNotNullTo null
    "$root/$relative"
}
