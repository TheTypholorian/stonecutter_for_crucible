@file:OptIn(StonecutterInternalAPI::class)

package dev.kikugie.stonecutter.build.data

import dev.kikugie.semver.data.Version
import dev.kikugie.stitcher.issue.BailException
import dev.kikugie.stitcher.issue.ProblemConsumer
import dev.kikugie.stitcher.issue.ProblemReport
import dev.kikugie.stitcher.issue.ProblemSeverity
import dev.kikugie.stitcher.transformer.TransformParameters
import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import dev.kikugie.stonecutter.build.config.ConstantContainer
import dev.kikugie.stonecutter.build.config.DependencyContainer
import dev.kikugie.stonecutter.build.config.ReplacementContainerImpl
import dev.kikugie.stonecutter.build.config.SwapContainer
import dev.kikugie.stonecutter.build.task.StonecutterBuildTasks
import dev.kikugie.stonecutter.build.task.StonecutterErrorsService.Companion.collect
import dev.kikugie.stonecutter.build.task.StonecutterErrorsService.Companion.log
import dev.kikugie.stonecutter.build.task.forFile
import dev.kikugie.stonecutter.controller.StonecutterControllerExtension
import dev.kikugie.stonecutter.controller.file.FileHandlerContainer
import dev.kikugie.stonecutter.controller.file.HandlerModel
import dev.kikugie.stonecutter.controller.flag.StonecutterFlagsView
import dev.kikugie.stonecutter.controller.tree.ParametersModel
import dev.kikugie.stonecutter.data.container.GradleContainerExtension.Companion.getContainer
import dev.kikugie.stonecutter.data.tree.ProjectNode
import dev.kikugie.stonecutter.data.version.LenientOperations
import dev.kikugie.stonecutter.data.version.VersionOperations
import dev.kikugie.stonecutter.settings.properties.StructuredPropertyConfigImpl
import dev.kikugie.stonecutter.settings.properties.StructuredPropertiesProvider
import dev.kikugie.stonecutter.util.overwriteText
import dev.kikugie.stonecutter.util.toRegularFile
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.the
import java.io.File
import java.nio.file.NoSuchFileException
import javax.inject.Inject

@OptIn(StonecutterInternalAPI::class)
internal abstract class StonecutterBuildConfiguration @Inject constructor(
    override val node: ProjectNode,
    factory: ProviderFactory,
    objects: ObjectFactory
) : Named, StonecutterBuildExtension, VersionOperations<Version> by LenientOperations {
    internal val controller: StonecutterControllerExtension = node.tree.project.the()
    internal val data: StonecutterBuildData = objects.newInstance(controller, current.version, factory)

    override val swaps: SwapContainer = objects.newInstance(data.swaps)
    override val constants: ConstantContainer = objects.newInstance(data.constants)
    override val dependencies: DependencyContainer = objects.newInstance(data.dependencies)
    override val replacements: ReplacementContainerImpl = objects.newInstance(data.replacements)
    override val filters: PatternFilterable = PatternSet()
    override val properties: StructuredPropertiesProvider.Config = setupDefaultProperties(node, objects)

    override val flags: StonecutterFlagsView = controller.flags
    override val tasks: StonecutterBuildTasks
        get() = throw UnsupportedOperationException("Build tasks are not available in the controller")

    override fun process(file: File, destination: String): File {
        val project = node.project
        val parameters = collectTransformParams(file, project, data)
        val problems: MutableCollection<ProblemReport> = mutableListOf()
        val reporter: ProblemConsumer = {
            if (it.severity == ProblemSeverity.ERROR) problems += it
            project.logger.log(it)
        }

        val contents = try {
            val text = project.providers.fileContents(file.toRegularFile(project.objects)).asText.orNull
                ?: throw NoSuchFileException(file.absolutePath)
            dev.kikugie.stitcher.process(file.toPath(), text, parameters, reporter)
        } catch (_: BailException) {
            problems.collect()
        }
        val output = project.layout.projectDirectory.file(destination)
            .also(project.providers::fileContents).asFile
        output.overwriteText(contents, true)
        return output
    }
}

@OptIn(StonecutterExperimentalAPI::class)
private fun setupDefaultProperties(node: ProjectNode, objects: ObjectFactory): StructuredPropertiesProvider.Config {
    val impl = StructuredPropertyConfigImpl(node.project, objects)
    impl.tags(node.branch.id, node.metadata.project)
    impl.loadDefaults(node.project.layout.projectDirectory)
    return impl
}

private fun collectTransformParams(file: File, project: Project, data: StonecutterBuildData): TransformParameters {
    val handlers = project.gradle.getContainer<FileHandlerContainer>()
        .handlers[file.extension.lowercase()].let { mapOf(it.name to HandlerModel(it)) }
    return ParametersModel(data).forFile(file.toPath(), handlers)!!
}
