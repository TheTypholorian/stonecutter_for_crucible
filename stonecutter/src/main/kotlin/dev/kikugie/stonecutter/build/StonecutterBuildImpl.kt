package dev.kikugie.stonecutter.build

import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.StonecutterPlugin
import dev.kikugie.stonecutter.build.data.StonecutterBuildConfiguration
import dev.kikugie.stonecutter.build.data.StonecutterBuildData
import dev.kikugie.stonecutter.build.task.StonecutterBuildTasksImpl
import dev.kikugie.stonecutter.controller.file.FileHandlerContainer
import dev.kikugie.stonecutter.controller.flag.StonecutterFlag
import dev.kikugie.stonecutter.controller.flag.StonecutterFlags
import dev.kikugie.stonecutter.controller.flag.StonecutterFlagsImpl
import dev.kikugie.stonecutter.controller.tree.toJsonString
import dev.kikugie.stonecutter.data.ProjectHierarchy.Companion.hierarchy
import dev.kikugie.stonecutter.data.container.ProjectNodeContainer
import dev.kikugie.stonecutter.data.container.getContainer
import dev.kikugie.stonecutter.data.tree.ProjectNode
import dev.kikugie.stonecutter.util.isIdeaSync
import dev.kikugie.stonecutter.util.projectDirectory
import dev.kikugie.stonecutter.util.requestTasks
import dev.kikugie.stonecutter.util.sourceSets
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileTree
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.withType
import javax.inject.Inject

internal abstract class StonecutterBuildImpl private constructor(val config: StonecutterBuildConfiguration) : StonecutterBuildExtension by config {
    @Inject constructor(project: Project) : this(project.findConfig())

    override val tasks: StonecutterBuildTasksImpl = project.objects.newInstance(this)

    init {
        project.plugins.apply("java")
        val extensions = project.gradle.getContainer<FileHandlerContainer>().extensions
        val handled: PatternFilterable = PatternSet().include { it.isDirectory || it.file.extension.lowercase() in extensions.get() }
        @Suppress("DEPRECATION") val processable: List<PatternFilterable> = listOf(handled, filters, config.files.processable.patterns)

        project.sourceSets.all {
            tasks.configureSource(this)
            createProcessingTasks(project, config.data, config.flags, processable, tasks)
        }
        project.refreshConfiguration(flags, tasks)
        project.configureManifest(flags, node)
        tasks.registerNodeModelTask()
    }
}

private fun Project.findConfig(): StonecutterBuildConfiguration = checkNotNull(gradle.getContainer<ProjectNodeContainer>()[this]) {
    "$hierarchy is not a registered Stonecutter node"
}

@OptIn(StonecutterInternalAPI::class)
private fun Project.refreshConfiguration(flags: StonecutterFlags, impls: StonecutterBuildTasksImpl): Unit = afterEvaluate {
    if (flags[StonecutterFlag.APPEND_SOURCES_AFTER_EVAL])
        sourceSets.forEach(impls::configureSource)

    if (flags[StonecutterFlag.GENERATE_SOURCES_ON_SYNC] && isIdeaSync) impls.generate.tasks
        .map { "${hierarchy.orEmpty()}:$it" }
        .let { gradle.requestTasks(it, path, projectDirectory) }
}

@OptIn(StonecutterInternalAPI::class)
private fun SourceSet.createProcessingTasks(
    project: Project,
    data: StonecutterBuildData,
    flags: StonecutterFlagsImpl,
    filters: List<PatternFilterable>,
    impls: StonecutterBuildTasksImpl
) {
    val layout = project.layout

    val name = name
    val sourceDir = "src/$name"
    val localSource = layout.projectDirectory.dir(sourceDir)
    val sharedSource = layout.projectDirectory.dir("../../$sourceDir")

    val generateTask = impls.registerGenerateTask(this) {
        params.set(data)
        root.set(sharedSource.asFile)
        source.setFrom(project.fileTree(sharedSource))
        processable.setFrom(filters.fold(project.fileTree(sharedSource), FileTree::matching))
        local.setFrom(project.fileTree(localSource))
        localRoot.set(localSource.asFile)
        destination.set(impls.generatedSourcesDir.dir(name))
        lineEnding.set(flags[StonecutterFlag.LINE_SEPARATOR])
        flagRegistry.set(project.provider { flags.registry.toJsonString() })
    }

    impls.registerMergeTask(this) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        from(generateTask.map { it.destination })
        into(sharedSource)
    }
}

private fun Project.configureManifest(flags: StonecutterFlags, node: ProjectNode) {
    if (!flags[StonecutterFlag.GENERATE_MANIFEST]) return

    val attributes = buildStonecutterManifest(node, gradle)
    tasks.withType<Jar>().configureEach {
        manifest.attributes(attributes)
    }
}

private fun buildStonecutterManifest(node: ProjectNode, gradle: Gradle): Map<String, String> = buildMap {
    this["Stonecutter-Plugin-Version"] = StonecutterPlugin.VERSION
    this["Stonecutter-Gradle-Version"] = gradle.gradleVersion
    this["Stonecutter-Node-Project"] = node.metadata.project
    this["Stonecutter-Node-Version"] = node.metadata.version
}
