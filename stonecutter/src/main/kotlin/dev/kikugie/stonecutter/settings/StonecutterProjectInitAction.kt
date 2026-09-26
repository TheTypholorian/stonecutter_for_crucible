package dev.kikugie.stonecutter.settings

import dev.kikugie.stonecutter.StonecutterPlugin
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import dev.kikugie.stonecutter.build.StonecutterBuildImpl
import dev.kikugie.stonecutter.controller.StonecutterControllerExtension
import dev.kikugie.stonecutter.controller.StonecutterControllerImpl
import dev.kikugie.stonecutter.controller.flag.StonecutterFlag
import dev.kikugie.stonecutter.controller.task.StonecutterModelTask
import dev.kikugie.stonecutter.controller.task.registerDefault
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.ProjectHierarchy.Companion.hierarchy
import dev.kikugie.stonecutter.data.container.FlagContainer
import dev.kikugie.stonecutter.data.container.ProjectBranchContainer
import dev.kikugie.stonecutter.data.container.TreeBuilderContainer
import dev.kikugie.stonecutter.data.container.getContainer
import dev.kikugie.stonecutter.settings.task.StonecutterIdeaConfigTask
import dev.kikugie.stonecutter.settings.tree.BranchBuilderImpl
import dev.kikugie.stonecutter.settings.tree.BuiltNodeInfo
import dev.kikugie.stonecutter.settings.tree.TreeBuilderImpl
import dev.kikugie.stonecutter.util.*
import org.gradle.api.IsolatedAction
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.service.ServiceRegistry
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugin.use.internal.PluginDependencyResolutionServices
import java.util.concurrent.ConcurrentHashMap

@Suppress("UnstableApiUsage")
internal object StonecutterProjectInitAction : IsolatedAction<Project> {
    override fun execute(target: Project) {
        // Funny staircase hehe
        val hierarchy: ProjectHierarchy = target.hierarchy
        if (hierarchy == ProjectHierarchy.ROOT) configureSyncTask(target)
        for (tree: TreeBuilderImpl in target.gradle.getContainer<TreeBuilderContainer>().collection)
            if (hierarchy == tree.hierarchy) return configureRoot(target, tree)
            else if (hierarchy.startsWith(tree.hierarchy)) for (branch: BranchBuilderImpl in tree.branchBuilders.values)
                if (hierarchy == branch.hierarchy) return configureBranch(target, tree)
                else if (hierarchy.startsWith(branch.hierarchy)) for (node: BuiltNodeInfo in branch.versions)
                    if (hierarchy == node.hierarchy) return configureNode(target, tree)
    }

    private fun configureSyncTask(project: Project): Unit = with(project) {
        val task = tasks.register<StonecutterIdeaConfigTask>("stonecutterIdea") {
            group = "stonecutter-impl"
            description = "Generates IntelliJ IDEA run configurations for version switch tasks"

            projects.set(ConcurrentHashMap())
            configurations.from(projectDirectory.resolve(".idea/runConfigurations"))
                .include { it.file.name.startsWith("Stonecutter") }
        }
        if (isIdeaSync)
            gradle.requestTasks(listOf(task.name), path, projectDir)
    }

    private fun configureRoot(project: Project, tree: TreeBuilderImpl): Unit = with(project) {
        val properties = StonecutterControllerImpl.setupDefaultProperties(project)
        val flags = gradle.getContainer<FlagContainer>().controller(tree.hierarchy, properties)
        plugins.withType<StonecutterPlugin> {
            project.createStonecutterExtension<StonecutterControllerExtension, StonecutterControllerImpl>(properties, flags)
        }
        if (flags[StonecutterFlag.AUTO_APPLY_PLUGIN]) autoApplyPlugin()
        if ("" in tree.branchBuilders) configureBranch(project, tree)
    }

    private fun configureBranch(project: Project, tree: TreeBuilderImpl): Unit = with(project) {
        val models = gradle.getContainer<ProjectBranchContainer>()
        val flags = gradle.getContainer<FlagContainer>()
        val controller = tree.hierarchy
        val branch = path
        registerDefault<StonecutterModelTask>("stonecutterSaveBranchModel") {
            enabled = flags[controller, StonecutterFlag.SERIALIZE_TREE_MODEL]
            output.set(layout.buildDirectory.file("stonecutter-cache/branch.json"))
            model.set(providers) { models[branch] ?: error("Branch model for '$branch' was not published") }
        }
    }

    private fun configureNode(project: Project, tree: TreeBuilderImpl): Unit = with(project) {
        plugins.withType<StonecutterPlugin> {
            project.createStonecutterExtension<StonecutterBuildExtension, StonecutterBuildImpl>()
        }
        if (gradle.getContainer<FlagContainer>()[tree.hierarchy, StonecutterFlag.AUTO_APPLY_PLUGIN])
            autoApplyPlugin()
    }

    private fun readResolve(): Any = this
}

private fun Project.autoApplyPlugin() {
    val services: ServiceRegistry = (this as ProjectInternal).services
    val repositories: RepositoryHandler = buildscript.repositories
    buildscript.configurations.getByName(ScriptHandler.CLASSPATH_CONFIGURATION).incoming.beforeResolve {
        if (repositories.isEmpty()) services.get(PluginDependencyResolutionServices::class.java)
            .createPluginResolveRepositories()
            .applyRepositoriesTo(repositories)
    }
    pluginManager.apply(StonecutterPlugin::class.java)
}
