package dev.kikugie.stonecutter.build

import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.StonecutterPlugin
import dev.kikugie.stonecutter.build.data.StonecutterBuildConfiguration
import dev.kikugie.stonecutter.build.data.StonecutterBuildData
import dev.kikugie.stonecutter.build.task.StonecutterBuildTasksImpl
import dev.kikugie.stonecutter.controller.file.FileHandlerContainer
import dev.kikugie.stonecutter.controller.flag.StonecutterFlag
import dev.kikugie.stonecutter.controller.flag.StonecutterFlagsView
import dev.kikugie.stonecutter.data.ProjectHierarchy.Companion.hierarchy
import dev.kikugie.stonecutter.data.container.GradleContainerExtension.Companion.getContainer
import dev.kikugie.stonecutter.data.container.ProjectNodeContainer
import dev.kikugie.stonecutter.data.tree.ProjectNode
import dev.kikugie.stonecutter.util.isIdeaSync
import dev.kikugie.stonecutter.util.projectDirectory
import dev.kikugie.stonecutter.util.requestTasks
import dev.kikugie.stonecutter.util.sourceSets
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.withType
import javax.inject.Inject

internal abstract class StonecutterBuildImpl private constructor(val config: StonecutterBuildConfiguration)
    : StonecutterBuildExtension by config {
    @Inject constructor(project: Project) : this(project.findConfig())
    override val tasks: StonecutterBuildTasksImpl = project.objects.newInstance(this)

    init {
        project.plugins.apply("java")
        project.sourceSets.all {
            tasks.configureSource(this)
            createProcessingTasks(project, config.data, filters, tasks)
        }
        project.refreshConfiguration(flags, tasks)
        project.configureManifest(flags, node)
        tasks.registerNodeModelTask()

        val handlers = project.gradle.getContainer<FileHandlerContainer>().handlers
        filters.include {
            it.isDirectory || it.file.extension.lowercase() in handlers.names
        }
    }
}

private fun Project.findConfig(): StonecutterBuildConfiguration = checkNotNull(gradle.getContainer<ProjectNodeContainer>()[this]) {
    "$hierarchy is not a registered Stonecutter node"
}

private fun Project.refreshConfiguration(flags: StonecutterFlagsView, impls: StonecutterBuildTasksImpl): Unit = afterEvaluate {
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
    filters: PatternFilterable,
    impls: StonecutterBuildTasksImpl
) {
    val layout = project.layout

    val name = name
    val sourceDir = "src/$name"
    val localSource = layout.projectDirectory.dir(sourceDir)
    val sharedSource = layout.projectDirectory.dir("../../$sourceDir")

    val sharedSourceFile = sharedSource.asFile
    val prepareTask = impls.registerPrepareTask(this) {
        params.set(data)
        root.set(sharedSourceFile)
        source.setFrom(project.fileTree(sharedSource).matching(filters))
        destination.set(impls.processedCacheDir.dir(name))
    }

    val localSourceFile = localSource.asFile
    impls.registerGenerateTask(this) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        from(sharedSource, prepareTask.map { it.destination })
        exclude { !it.isDirectory && it.relativePath.getFile(localSourceFile).exists() }
        into(impls.generatedSourcesDir.dir(name))
    }

    impls.registerMergeTask(this) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        from(prepareTask.map { it.destination })
        into(sharedSource)
    }
}

private fun Project.configureManifest(flags: StonecutterFlagsView, node: ProjectNode) {
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
