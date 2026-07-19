package dev.kikugie.stonecutter.data.container

import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import dev.kikugie.stonecutter.build.data.StonecutterBuildConfiguration
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.ProjectHierarchy.Companion.hierarchy
import dev.kikugie.stonecutter.data.tree.ProjectNode
import dev.kikugie.stonecutter.data.tree.ProjectTree
import dev.kikugie.stonecutter.settings.tree.TreeBuilderImpl
import org.gradle.api.*
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.registerIfAbsent
import javax.inject.Inject

/**Represents a Gradle-level extension used for storing per-project data.*/
internal interface GradleContainerService : BuildService<BuildServiceParameters.None> {
    @get:Inject
    val objects: ObjectFactory

    companion object {
        inline fun <reified T : GradleContainerService> Gradle.createContainer(): Provider<T> =
            sharedServices.registerIfAbsent(requireNotNull(T::class.simpleName) { "Provided class has no name" }, T::class)

        inline fun <reified T : GradleContainerService> Gradle.getContainer(): T {
            val name = requireNotNull(T::class.simpleName) { "Provided class has no name" }
            val service = checkNotNull(sharedServices.registrations.findByName(name)) { "No service registered for name $name" }
            return service.service.get() as T
        }
    }
}

internal abstract class TreeBuilderContainer : GradleContainerService {
    abstract val collection: NamedDomainObjectSet<TreeBuilderImpl>

    operator fun get(hierarchy: ProjectHierarchy): TreeBuilderImpl? =
        collection.findByName("TreeBuilderImpl@$hierarchy")

    operator fun plusAssign(builder: TreeBuilderImpl): Unit = synchronized(collection) {
        collection.add(builder)
    }
}

internal abstract class ProjectNodeContainer : GradleContainerService {
    abstract val projects: NamedDomainObjectSet<ProjectNode>

    abstract val configs: NamedDomainObjectSet<StonecutterBuildConfiguration>

    operator fun get(project: Project): StonecutterBuildConfiguration? = synchronized(configs) {
        val node = projects.findByName("ProjectNode@${project.hierarchy}")
            ?: return@synchronized null
        val name = "StonecutterBuildConfiguration@${project.hierarchy}"
        configs.findByName(name) ?: objects.newInstance<StonecutterBuildConfiguration>(name, node, project)
            .also(configs::add)
    }

    operator fun plusAssign(tree: ProjectTree): Unit = synchronized(projects) {
        for (node in tree.nodes) projects.add(node)
    }

    fun configure(tree: ProjectTree, action: Action<StonecutterBuildExtension>): Unit = synchronized(configs) {
        val nodes = tree.nodes.map { "StonecutterBuildConfiguration@${it.hierarchy}" }.toSet()
        configs.named { it in nodes }.all(action)
    }
}

