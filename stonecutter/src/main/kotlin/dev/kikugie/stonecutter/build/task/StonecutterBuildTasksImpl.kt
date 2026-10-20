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
import dev.kikugie.stonecutter.util.SCJSON
import dev.kikugie.stonecutter.util.allSources
import dev.kikugie.stonecutter.util.set
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternSet
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

@OptIn(StonecutterInternalAPI::class)
internal abstract class StonecutterBuildTasksImpl @Inject constructor(val build: StonecutterBuildImpl) : StonecutterBuildTasks {
    override val generate: TaskProviderMap<StonecutterGenerateTask> = TaskProviderMap(build.project) { "stonecutterGenerate${it.sourceSetSuffix}" }
    override val merge: TaskProviderMap<Copy> = TaskProviderMap(build.project) { "stonecutterMerge${it.sourceSetSuffix}" }

    @PublishedApi
    internal val generateAll: TaskProvider<DefaultTask> = build.project.registerDefault<DefaultTask>("stonecutterGenerateAll")

    @PublishedApi
    internal val mergeAll: TaskProvider<DefaultTask> = build.project.registerDefault<DefaultTask>("stonecutterMergeAll")

    private val registeredSources: MutableSet<File> = mutableSetOf()
    private val filteredSources: MutableSet<SourceDirectorySet> = mutableSetOf()
    private val logger: Logger = Logging.getLogger("StonecutterBuild")

    init {
        val buildDir = build.project.layout.buildDirectory
        generatedSourcesDir.value(buildDir.dir("generated/stonecutter")).finalizeValue()
    }

    override fun configureSource(src: SourceSet) {
        val branchSrc: File = build.branch.location.resolve("src").toFile()
        val versionSrc: File = build.node.location.resolve("src").toFile()
        val generatedSrc: File = generatedSourcesDir.asFile.get()
        val taskName: String = generate.taskName(src.name)
        val disabled = DisabledSourceSpec(
            listOf(branchSrc.resolve(src.name), generatedSrc.resolve(src.name)),
            build.config.data.files.map(FilesModel::disabled)
        )

        for (entry in src.allSources()) {
            val matching = entry.sourceDirectories
                .map { it.relativeTo(versionSrc) }
                .filterNot { it.startsWith("..") }

            entry.extend(matching, branchSrc, null) { build.current.isActive }
            entry.extend(matching, generatedSrc, taskName) { !build.current.isActive }
            if (filteredSources.add(entry)) entry.exclude(disabled)
        }
    }

    inline fun registerGenerateTask(src: SourceSet, crossinline config: StonecutterGenerateTask.() -> Unit): TaskProvider<StonecutterGenerateTask> =
        build.project.registerDefault(generate.taskName(src.name), { task ->
            generate.identifiers += src.name
            generateAll.configure { dependsOn(task) }
        }, config)

    inline fun registerMergeTask(src: SourceSet, crossinline config: Copy.() -> Unit): TaskProvider<Copy> =
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

    private fun SourceDirectorySet.extend(matching: Iterable<File>, root: File, task: String?, apply: () -> Boolean) {
        val filtered: List<File> = matching.map(root::resolve).filterNot(registeredSources::contains)
            .ifEmpty { return }

        val collection: ConfigurableFileCollection = build.project.files({ if (apply()) filtered else emptyList() })
        if (task != null) collection.builtBy({ if (apply()) "${build.node.hierarchy}:$task" else null })
        srcDir(collection)

        registeredSources += filtered
        if (logger.isDebugEnabled) for (dir in filtered) logger.debug(buildString {
            append("Registered source directory ${dir.absolutePath}")
            if (task != null) append(" << $task")
        })
    }
}

private class DisabledSourceSpec(private val roots: List<File>, private val patterns: Provider<Set<String>>) : Spec<FileTreeElement> {
    @Transient private var _matcher: Spec<FileTreeElement>? = null
    private val matcher: Spec<FileTreeElement>
        get() = _matcher ?: createMatcher().also { _matcher = it }

    override fun isSatisfiedBy(element: FileTreeElement): Boolean {
        if (element.isDirectory) return false
        val root: File = roots.firstOrNull(element.file::startsWith) ?: return false
        val path: RelativePath = RelativePath.parse(!element.isDirectory, element.file.relativeTo(root).invariantSeparatorsPath)
        return matcher.isSatisfiedBy(RelocatedElement(element, path))
    }

    private fun createMatcher(): Spec<FileTreeElement> = patterns.get().let {
        if (it.isEmpty()) Specs.satisfyNone() else PatternSet().include(it).asSpec
    }
}

private class RelocatedElement(private val delegate: FileTreeElement, private val path: RelativePath) : FileTreeElement by delegate {
    override fun getRelativePath(): RelativePath = path
    override fun getPath(): String = path.pathString
    override fun getName(): String = path.lastName
}

private val String.sourceSetSuffix: String
    get() = if (this == "main") "" else capitaliseFirstChar()