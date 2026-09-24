package dev.kikugie.stonecutter.build.task

import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.build.StonecutterBuildImpl
import dev.kikugie.stonecutter.controller.flag.StonecutterFlag
import dev.kikugie.stonecutter.controller.task.StonecutterModelTask
import dev.kikugie.stonecutter.controller.task.registerDefault
import dev.kikugie.stonecutter.controller.tree.toModel
import dev.kikugie.stonecutter.data.collection.TaskProviderMap
import dev.kikugie.stonecutter.util.SCJSON
import dev.kikugie.stonecutter.util.allSources
import dev.kikugie.stonecutter.util.set
import org.gradle.api.DefaultTask
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import java.io.File
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

        for (entry in src.allSources()) {
            val matching = entry.sourceDirectories
                .map { it.relativeTo(versionSrc) }
                .filterNot { it.startsWith("..") }

            if (build.current.isActive) entry.extend(matching, branchSrc, null, true)
            entry.extend(matching, generatedSrc, taskName, !build.current.isActive)
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

    private fun SourceDirectorySet.extend(matching: Iterable<File>, root: File, task: String?, apply: Boolean) {
        val filtered = matching.map(root::resolve).filterNot(registeredSources::contains)
            .ifEmpty { return }

        if (apply) {
            val collection = build.project.files(filtered)
            if (task != null) collection.builtBy("${build.node.hierarchy}:$task")
            srcDir(collection)
        }

        registeredSources += filtered
        if (logger.isDebugEnabled) for (dir in filtered) logger.debug(buildString {
            append("Registered source directory ${dir.absolutePath}")
            if (task != null) append(" << $task")
        })
    }
}

private val String.sourceSetSuffix: String
    get() = if (this == "main") "" else replaceFirstChar(Char::uppercase)