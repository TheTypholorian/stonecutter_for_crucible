package dev.kikugie.stonecutter.build.data

import dev.kikugie.commons.result.unsupportedOperation
import dev.kikugie.semver.data.Version
import dev.kikugie.stitcher.Stitcher
import dev.kikugie.stitcher.StitcherParameters
import dev.kikugie.stitcher.document.SourceDocument
import dev.kikugie.stitcher.document.io.LineNormalizingReader
import dev.kikugie.stitcher.document.io.LineNormalizingWriter
import dev.kikugie.stitcher.document.issue.BailException
import dev.kikugie.stitcher.document.issue.DefaultProblemReporter
import dev.kikugie.stitcher.document.issue.ProblemReport
import dev.kikugie.stitcher.token.appendTo
import dev.kikugie.stitcher.token.type.RootScopeState
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import dev.kikugie.stonecutter.build.config.*
import dev.kikugie.stonecutter.build.task.StonecutterBuildTasks
import dev.kikugie.stonecutter.build.task.StonecutterErrorsService.Companion.collect
import dev.kikugie.stonecutter.build.task.StonecutterErrorsService.Companion.log
import dev.kikugie.stonecutter.controller.file.FileHandlerContainer
import dev.kikugie.stonecutter.controller.file.HandlerModel
import dev.kikugie.stonecutter.controller.flag.StonecutterFlag
import dev.kikugie.stonecutter.controller.flag.StonecutterFlagsImpl
import dev.kikugie.stonecutter.controller.tree.*
import dev.kikugie.stonecutter.data.container.FlagContainer
import dev.kikugie.stonecutter.data.container.getContainer
import dev.kikugie.stonecutter.data.property.StructuredPropertiesImpl
import dev.kikugie.stonecutter.data.tree.ProjectNode
import dev.kikugie.stonecutter.data.version.LenientOperations
import dev.kikugie.stonecutter.data.version.VersionOperations
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import dev.kikugie.stonecutter.util.newInstance
import dev.kikugie.stonecutter.util.overwriteText
import dev.kikugie.stonecutter.util.sourceRoots
import dev.kikugie.stonecutter.util.toRegularFile
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.newInstance
import java.io.File
import java.io.StringWriter
import java.nio.file.NoSuchFileException
import javax.inject.Inject
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo

internal abstract class StonecutterBuildConfiguration @Inject constructor(
    override val node: ProjectNode, override val project: Project, objects: ObjectFactory
) : Named, StonecutterBuildExtension, VersionOperations<Version> by LenientOperations {
    override val properties: StructuredPropertiesImpl = project.setupDefaultProperties(node, objects)
    override val flags: StonecutterFlagsImpl = project.gradle.getContainer<FlagContainer>()
        .node(node.hierarchy, node.tree.hierarchy, properties)
    internal val data: StonecutterBuildData = objects.newInstance(flags, current.version, project.providers)

    override val swaps: SwapContainer = objects.newInstance(data.swaps)
    override val constants: ConstantContainer = objects.newInstance(data.constants)
    override val targets: TargetContainer = objects.newInstance(data.dependencies)

    @Suppress("DEPRECATION")
    @Deprecated("Stonecutter dependencies were renamed to targets", ReplaceWith("targets"))
    override val dependencies: DependencyContainer = objects.newInstance(targets)
    override val replacements: ReplacementContainerImpl = objects.newInstance(data.replacements)
    @Deprecated("File filters were moved to `files`", ReplaceWith("files"))
    override val filters: PatternFilterable = PatternSet()
    override val files: StonecutterFilesImpl = StonecutterFilesImpl()

    init {
        data.files.set(project.provider(files::toModel))
        properties.configureExport(flags[StonecutterFlag.EXPOSE_PROPERTIES], project.extra)
        properties.configureWarnings(flags[StonecutterFlag.WARN_DUPLICATE_PROPERTIES])
    }

    override val tasks: StonecutterBuildTasks
        get() = unsupportedOperation("Build tasks are not available in the controller")

    override fun process(file: File, destination: String): File {
        val parameters: StitcherParameters = project.collectTransformParams(file, node, data, flags.registry)
        val problems: MutableCollection<ProblemReport> = mutableListOf()
        val reporter = DefaultProblemReporter({
            if (it.severity == ProblemReport.Severity.ERROR) problems += it
            project.logger.log(it)
        })

        val raw: String = project.providers.fileContents(file.toRegularFile(project.objects)).asText.orNull
            ?: throw NoSuchFileException(file.absolutePath)
        val reader = LineNormalizingReader(raw.reader())
        val text: String = reader.use { it.readText() }
        val modified: String = try {
            val document = SourceDocument(file.toPath(), text, reporter)
            buildString { Stitcher.process(document, parameters).appendTo(this) }
        } catch (_: BailException) {
            problems.collect()
        }

        val output = project.layout.projectDirectory.file(destination)
        val writer = StringWriter()
        val separator = flags[StonecutterFlag.LINE_SEPARATOR]
            .ifEmpty { reader.separator ?: System.lineSeparator() }
        LineNormalizingWriter(writer, separator).use { it.write(modified) }
        output.asFile.overwriteText(writer.toString(), true)
        project.providers.fileContents(output).asBytes.orNull
        return output.asFile
    }
}

private fun Project.setupDefaultProperties(node: ProjectNode, objects: ObjectFactory): StructuredPropertiesImpl = objects.newInstance(providers) {
    tags(node.branch.id, node.metadata.project)
    loadDefaults(layout.settingsDirectory)
    val branchRelative: String = node.branch.location.relativeTo(node.location).invariantSeparatorsPathString
    loadDefaults(layout.projectDirectory.dir(branchRelative))
    val treeRelative: String = node.tree.location.relativeTo(node.location).invariantSeparatorsPathString
    loadDefaults(layout.projectDirectory.dir(treeRelative))
    holder.extraProperties = extra
    holder.reload()
}

private fun Project.collectTransformParams(
    file: File,
    node: ProjectNode,
    data: StonecutterBuildData,
    flags: ConfigurationRegistry
): StitcherParameters {
    val handlers = gradle.getContainer<FileHandlerContainer>()
        .handlers[file.extension.lowercase()].let { mapOf(it.name to HandlerModel(it)) }
    val resolved: File = file(file).normalize()
    val parameters = data.toParameters()
    val source: File? = sourceRoot(resolved, node.branch.location.toFile())
    val scopes = source
        ?.let { parameters.replacements.resolveFiles(fileTree(it)) }
        ?: parameters.replacements.unmatchedFiles()
    val state = source
        ?.let { parameters.files.resolveStates(fileTree(it))[resolved] }
        ?: RootScopeState.ANY
    return parameters.restrictedTo(resolved, scopes).forFile(resolved.toPath(), handlers, flags, state)!!
}

private fun Project.sourceRoot(file: File, branch: File): File? {
    val sets: SourceSetContainer = extensions.findByType<SourceSetContainer>()
        ?: return null
    return sets.asSequence()
        .flatMap { sourceRoots(it, branch).values }
        .flatMap { sequenceOf(it.local, it.shared) }
        .filter(file::startsWith)
        .maxByOrNull { it.path.length }
}
