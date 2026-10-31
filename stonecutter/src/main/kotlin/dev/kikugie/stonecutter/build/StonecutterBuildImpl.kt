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
import dev.kikugie.stonecutter.util.*
import org.gradle.api.Project
import org.gradle.api.file.DirectoryTree
import org.gradle.api.file.FileTree
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.withType
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

internal abstract class StonecutterBuildImpl private constructor(val config: StonecutterBuildConfiguration) : StonecutterBuildExtension by config {
    @Inject constructor(project: Project) : this(project.findConfig())

    override val tasks: StonecutterBuildTasksImpl = project.objects.newInstance(this)

    init {
        val extensions = project.gradle.getContainer<FileHandlerContainer>().extensions
        val handled: PatternFilterable = PatternSet().include { it.isDirectory || it.file.extension.lowercase() in extensions.get() }
        @Suppress("DEPRECATION") val processable: List<PatternFilterable> = listOf(handled, filters, config.files.processable.patterns)

        project.plugins.withType<JvmEcosystemPlugin> {
            project.sourceSets.all {
                tasks.configureSource(this)
                createProcessingTasks(project, config.data, config.flags, processable, tasks)
            }
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
        extensions.findByType<SourceSetContainer>()?.forEach(impls::configureSource)

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
    val name = name
    val branch: File = impls.build.branch.location.toFile()
    val roots: Provider<Map<File, SourceRoot>> = project.provider { project.sourceRoots(this, branch) }
    val trees: Provider<List<Pair<SourceRoot, DirectoryTree>>> = project.provider {
        val roots: Map<File, SourceRoot> = project.sourceRoots(this, branch)
        allSources().flatMap { it.srcDirTrees }.mapNotNull { tree -> roots[tree.dir]?.to(tree) }.toList()
    }

    val generateTask = impls.registerGenerateTask(this) {
        params.set(data)
        sourceRoots.set(roots.map { it.values.associate { root -> root.name to root.shared } })
        overrideRoots.set(roots.map { it.values.associate { root -> root.name to root.local } })
        sources.from(Callable { trees.get().map { (root, tree) -> project.fileTree(root.shared).matching(tree.patterns) } })
        sources.from(Callable {
            val disabled: Set<String> = data.files.get().disabled
            if (disabled.isEmpty()) emptyList()
            else roots.get().values.map { project.fileTree(it.shared).matching(PatternSet().include(disabled)) }
        })
        overrides.from(Callable { trees.get().map { (root, tree) -> project.fileTree(root.local).matching(tree.patterns) } })
        processable.from(Callable { roots.get().values.map { filters.fold(project.fileTree(it.shared), FileTree::matching) } })
        destination.set(impls.generatedSourcesDir.dir(name))
        lineEnding.set(flags[StonecutterFlag.LINE_SEPARATOR])
        flagRegistry.set(project.provider { flags.registry.toJsonString() })
    }

    impls.registerMergeTask(this) {
        generated.set(generateTask.flatMap { it.destination })
        sourceRoots.set(generateTask.flatMap { it.sourceRoots })
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
