package dev.kikugie.stonecutter.build.task

import dev.kikugie.commons.text.capitaliseFirstChar
import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.build.StonecutterBuildImpl
import dev.kikugie.stonecutter.controller.flag.StonecutterFlag
import dev.kikugie.stonecutter.controller.task.StonecutterModelTask
import dev.kikugie.stonecutter.controller.task.registerDefault
import dev.kikugie.stonecutter.controller.tree.toModel
import dev.kikugie.stonecutter.data.collection.TaskProviderMap
import dev.kikugie.stonecutter.model.process.FilesModel
import dev.kikugie.stonecutter.util.*
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternSet
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

@OptIn(StonecutterInternalAPI::class)
internal abstract class StonecutterBuildTasksImpl @Inject constructor(val build: StonecutterBuildImpl) : StonecutterBuildTasks {
    override val generate: TaskProviderMap<StonecutterGenerateTask> = TaskProviderMap(build.project) { "stonecutterGenerate${it.sourceSetSuffix}" }
    override val merge: TaskProviderMap<StonecutterMergeTask> = TaskProviderMap(build.project) { "stonecutterMerge${it.sourceSetSuffix}" }

    @PublishedApi
    internal val generateAll: TaskProvider<DefaultTask> = build.project.registerDefault<DefaultTask>("stonecutterGenerateAll")

    @PublishedApi
    internal val mergeAll: TaskProvider<DefaultTask> = build.project.registerDefault<DefaultTask>("stonecutterMergeAll")

    private val filteredSources: MutableSet<SourceDirectorySet> = mutableSetOf()
    private val logger: Logger = Logging.getLogger("StonecutterBuild")

    init {
        val buildDir = build.project.layout.buildDirectory
        generatedSourcesDir.value(buildDir.dir("generated/stonecutter")).disallowChanges()
    }

    override fun configureSource(src: SourceSet) {
        val project = build.project
        val branch: File = build.branch.location.toFile()
        val generatedSrc: () -> File = { generatedSourcesDir.asFile.get().resolve(src.name) }
        val taskName: String = generate.taskName(src.name)
        val active: Boolean = build.current.isActive
        val disabled = DisabledSourceSpec(
            project.layout.projectDirectory.asFile,
            project.layout.buildDirectory,
            build.config.data.files.map(FilesModel::disabled)
        )

        val roots: Map<File, SourceRoot> = project.sourceRoots(src, branch)
        for (entry in src.allSources()) {
            val present: Set<File> = entry.sourceDirectories.files
            val missing: List<SourceRoot> = present.mapNotNull(roots::get).filterNot {
                (if (active) it.shared else generatedSrc().resolve(it.name)) in present
            }

            entry.extend(missing, null, { build.current.isActive }) { it.shared }
            entry.extend(missing, taskName, { !build.current.isActive }) { generatedSrc().resolve(it.name) }
            if (filteredSources.add(entry)) entry.exclude(disabled)
        }
    }

    inline fun registerGenerateTask(src: SourceSet, crossinline config: StonecutterGenerateTask.() -> Unit): TaskProvider<StonecutterGenerateTask> =
        build.project.registerDefault(generate.taskName(src.name), { task ->
            generate.identifiers += src.name
            generateAll.configure { dependsOn(task) }
        }, config)

    inline fun registerMergeTask(src: SourceSet, crossinline config: StonecutterMergeTask.() -> Unit): TaskProvider<StonecutterMergeTask> =
        build.project.registerDefault(merge.taskName(src.name), { task ->
            merge.identifiers += src.name
            mergeAll.configure { dependsOn(task) }
        }, config)

    fun registerNodeModelTask(): TaskProvider<StonecutterModelTask> = build.project.registerDefault("stonecutterSaveNodeModel") {
        val node = build.node

        enabled = build.config.flags[StonecutterFlag.SERIALIZE_TREE_MODEL]
        output.set(build.project.layout.buildDirectory.file("stonecutter-cache/node.json"))
        model.set(build.project.providers) {
            node.toModel(build.config.data, build.config.flags.registry).let(SCJSON::encodeToString)
        }
    }

    private fun SourceDirectorySet.extend(roots: List<SourceRoot>, task: String?, apply: () -> Boolean, location: (SourceRoot) -> File) {
        if (roots.isEmpty()) return

        val collection: ConfigurableFileCollection = build.project.files(Callable { if (apply()) roots.map(location) else emptyList() })
        if (task != null) collection.builtBy(Callable { if (apply()) "${build.node.hierarchy}:$task" else null })
        srcDir(collection)

        if (logger.isDebugEnabled) for (root in roots) logger.debug(buildString {
            append("Registered source directory ${location(root).absolutePath}")
            if (task != null) append(" << $task")
        })
    }
}

private class DisabledSourceSpec(
    private val project: File,
    private val build: Provider<Directory>,
    private val patterns: Provider<Set<String>>
) : Spec<FileTreeElement> {
    @Transient private var _matcher: Spec<FileTreeElement>? = null
    private val matcher: Spec<FileTreeElement>
        get() = _matcher ?: createMatcher().also { _matcher = it }

    override fun isSatisfiedBy(element: FileTreeElement): Boolean = when {
        element.isDirectory -> false
        element.file.startsWith(project) && !element.file.startsWith(build.get().asFile) -> false
        else -> matcher.isSatisfiedBy(element)
    }

    private fun createMatcher(): Spec<FileTreeElement> = patterns.get().let {
        if (it.isEmpty()) Specs.satisfyNone() else PatternSet().include(it).asSpec
    }
}

private val String.sourceSetSuffix: String
    get() = if (this == "main") "" else capitaliseFirstChar()