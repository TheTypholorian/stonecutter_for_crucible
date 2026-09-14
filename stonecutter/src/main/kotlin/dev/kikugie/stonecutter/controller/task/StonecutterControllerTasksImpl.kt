package dev.kikugie.stonecutter.controller.task

import dev.kikugie.commons.takeAs
import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.controller.StonecutterControllerImpl
import dev.kikugie.stonecutter.controller.StonecutterControllerManager
import dev.kikugie.stonecutter.controller.flag.StonecutterFlagsImpl
import dev.kikugie.stonecutter.controller.tree.toModel
import dev.kikugie.stonecutter.data.container.GradleContainerService.Companion.getContainer
import dev.kikugie.stonecutter.data.container.ProjectBranchContainer
import dev.kikugie.stonecutter.data.tree.ProjectNode
import dev.kikugie.stonecutter.data.tree.ProjectTree
import dev.kikugie.stonecutter.data.collection.TaskProviderMap
import dev.kikugie.stonecutter.util.SCJSON
import dev.kikugie.stonecutter.util.set
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.register
import java.io.File
import javax.inject.Inject

internal abstract class StonecutterControllerTasksImpl @Inject constructor(val controller: StonecutterControllerImpl) : StonecutterControllerTasks() {
    override val switch: TaskProviderMap<StonecutterSwitchTask> = TaskProviderMap(controller.root) {
        "stonecutterSwitchTo$it"
    }

    override fun namedImpl(name: String, cls: Class<out Task>?, filter: ProjectNode.() -> Boolean): MapProperty<ProjectNode, TaskProvider<*>> {
        val selector: TaskSelector =
            if (cls == null) TaskSelector { it.project.tasks.named(name) }
            else TaskSelector { it.project.tasks.named(name, cls) }

        val property: MapProperty<ProjectNode, TaskProvider<*>> = controller.root.objects.mapProperty()
        for (node in controller.tree.nodes.filter(filter))
            property.put(node, controller.root.provider { selector.get(node) })

        return property
    }

    @OptIn(StonecutterInternalAPI::class)
    override fun orderImpl(name: String, ordering: Comparator<ProjectNode>, filter: ProjectNode.() -> Boolean) {
        val lock = StonecutterTaskMutexService.create(controller.root.gradle, name)
        val nodes = controller.tree.nodes.filter(filter).sortedWith(ordering)
        if (nodes.size <= 1) return

        val dependencies = mutableListOf(nodes.first().task(name))
        for (node in nodes.drop(1)) {
            val copy = dependencies.toTypedArray()
            dependencies += node.task(name)
            node.project.afterEvaluate {
                if (name in tasks.names) tasks.named(name) {
                    usesService(lock)
                    mustRunAfter(*copy)
                }
            }
        }
    }

    fun registerScriptSwitchTask(id: Identifier, manager: StonecutterControllerManager): TaskProvider<out StonecutterSwitchTask> {
        val delegates = controller.tree.collectMergeTasks(id)
        return controller.root.registerDefault<StonecutterScriptSwitchTask>(switch.taskName(id), { switch.identifiers += id }) {
            version.set(id)
            writer.set(manager::update)
            file.set(controller.root.buildFile)
            dependsOn(delegates)
        }
    }

    fun registerExternalSwitchTask(id: Identifier, provider: File): TaskProvider<out StonecutterSwitchTask> {
        val delegates = controller.tree.collectMergeTasks(id)
        return controller.root.registerDefault<StonecutterExternalSwitchTask>(switch.taskName(id), { switch.identifiers += id }) {
            version.set(id)
            file.set(provider)
            dependsOn(delegates)
        }
    }

    fun registerModelGroupingTask() = controller.root.registerDefault<DefaultTask>("stonecutterSaveModels") {
        dependsOn(controller.tree.hierarchy.resolve("stonecutterSaveTreeModel").toString())
        for (branch in controller.tree.branches)
            dependsOn(branch.hierarchy.resolve("stonecutterSaveBranchModel").toString())
        for (node in controller.tree.nodes)
            dependsOn(node.hierarchy.resolve("stonecutterSaveNodeModel").toString())
    }

    fun registerTreeModelTask() = controller.root.registerDefault<StonecutterModelTask>("stonecutterSaveTreeModel") {
        val tree = controller.tree
        val flags = controller.flags.takeAs<StonecutterFlagsImpl>()

        output.set(controller.root.layout.buildDirectory.file("stonecutter-cache/tree.json"))
        model.set(controller.root.providers) {
            tree.toModel(flags.registry).let(SCJSON::encodeToString)
        }
    }

    // The main branch is the controller's own project, so its task lives here.
    // Non-main branches register their own tasks from the models published by
    // publishBranchModels().
    fun registerBranchModelTask() {
        val main = controller.tree.branches.firstOrNull { it.id.isEmpty() } ?: return
        controller.root.registerDefault<StonecutterModelTask>("stonecutterSaveBranchModel") {
            output.set(controller.root.layout.buildDirectory.file("stonecutter-cache/branch.json"))
            model.set(controller.root.providers) {
                main.toModel().let(SCJSON::encodeToString)
            }
        }
    }

    fun publishBranchModels() {
        val container = controller.root.gradle.getContainer<ProjectBranchContainer>()
        for (branch in controller.tree.branches) if (branch.id.isNotEmpty())
            container[branch.hierarchy.toString()] = branch.toModel().let(SCJSON::encodeToString)
    }

    private fun interface TaskSelector {
        fun get(node: ProjectNode): TaskProvider<*>
    }
}

private fun ProjectNode.task(name: String): String =
    "${hierarchy.orEmpty()}:$name"

private fun ProjectTree.collectMergeTasks(project: Identifier): List<String> = nodes
    .filter { it.metadata.project == project }
    .map { it.hierarchy.resolve("stonecutterMergeAll").toString() }

internal inline fun <reified T : Task> Project.registerDefault(
    name: String,
    consumer: (TaskProvider<T>) -> Unit = {},
    crossinline config: T.() -> Unit = {}
): TaskProvider<T> = tasks.register<T>(name) {
    group = "stonecutter-impl"
    description = "Internal Stonecutter task. Do not call manually."
    config()
}.apply(consumer)
