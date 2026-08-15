package dev.kikugie.stonecutter.build.data

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
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import dev.kikugie.stonecutter.build.config.ConstantContainer
import dev.kikugie.stonecutter.build.config.DependencyContainer
import dev.kikugie.stonecutter.build.config.ReplacementContainerImpl
import dev.kikugie.stonecutter.build.config.SwapContainer
import dev.kikugie.stonecutter.build.config.TargetContainer
import dev.kikugie.stonecutter.build.task.StonecutterBuildTasks
import dev.kikugie.stonecutter.build.task.StonecutterErrorsService.Companion.collect
import dev.kikugie.stonecutter.build.task.StonecutterErrorsService.Companion.log
import dev.kikugie.stonecutter.controller.StonecutterControllerExtension
import dev.kikugie.stonecutter.controller.file.FileHandlerContainer
import dev.kikugie.stonecutter.controller.file.HandlerModel
import dev.kikugie.stonecutter.controller.flag.StonecutterFlagsView
import dev.kikugie.stonecutter.controller.tree.ParametersModel
import dev.kikugie.stonecutter.data.container.GradleContainerService.Companion.getContainer
import dev.kikugie.stonecutter.data.property.StructuredPropertiesExtension
import dev.kikugie.stonecutter.data.property.StructuredPropertiesImpl
import dev.kikugie.stonecutter.data.tree.ProjectNode
import dev.kikugie.stonecutter.data.version.LenientOperations
import dev.kikugie.stonecutter.data.version.VersionOperations
import dev.kikugie.stonecutter.util.newInstance
import dev.kikugie.stonecutter.util.overwriteText
import dev.kikugie.stonecutter.util.toRegularFile
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.the
import java.io.File
import java.io.StringWriter
import java.nio.file.NoSuchFileException
import javax.inject.Inject
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo

internal abstract class StonecutterBuildConfiguration @Inject constructor(
    override val node: ProjectNode, override val project: Project, objects: ObjectFactory
) : Named, StonecutterBuildExtension, VersionOperations<Version> by LenientOperations {
    internal val controller: StonecutterControllerExtension = node.tree.project.the()
    internal val data: StonecutterBuildData = objects.newInstance(controller, current.version, project.providers)

    override val swaps: SwapContainer = objects.newInstance(data.swaps)
    override val constants: ConstantContainer = objects.newInstance(data.constants)
    override val targets: TargetContainer = objects.newInstance(data.dependencies)

    @Suppress("DEPRECATION")
    @Deprecated("Stonecutter dependencies were renamed to targets", ReplaceWith("targets"))
    override val dependencies: DependencyContainer = objects.newInstance(targets)
    override val replacements: ReplacementContainerImpl = objects.newInstance(data.replacements)
    override val filters: PatternFilterable = PatternSet()
    override val properties: StructuredPropertiesExtension = project.setupDefaultProperties(node, objects)

    override val flags: StonecutterFlagsView = controller.flags
    override val tasks: StonecutterBuildTasks
        get() = throw UnsupportedOperationException("Build tasks are not available in the controller")

    override fun process(file: File, destination: String): File {
        val parameters: StitcherParameters = project.collectTransformParams(file, data)
        val problems: MutableCollection<ProblemReport> = mutableListOf()
        val reporter = DefaultProblemReporter({
            if (it.severity == ProblemReport.Severity.ERROR) problems += it
            project.logger.log(it)
        })

        val modified: String = try {
            val raw: String = project.providers.fileContents(file.toRegularFile(project.objects)).asText.orNull
                ?: throw NoSuchFileException(file.absolutePath)
            val text: String = LineNormalizingReader(raw.reader()).use { it.readText() }
            val document = SourceDocument(file.toPath(), text, reporter)
            buildString { Stitcher.process(document, parameters).appendTo(this) }
        } catch (_: BailException) {
            problems.collect()
        }

        val file = project.layout.projectDirectory.file(destination)
            .also(project.providers::fileContents).asFile
        val writer = StringWriter()
        LineNormalizingWriter(writer).write(modified)
        file.overwriteText(writer.toString(), true)
        return file
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

private fun Project.collectTransformParams(file: File, data: StonecutterBuildData): StitcherParameters {
    val handlers = gradle.getContainer<FileHandlerContainer>()
        .handlers[file.extension.lowercase()].let { mapOf(it.name to HandlerModel(it)) }
    return ParametersModel(data).forFile(file.toPath(), handlers)!!
}
