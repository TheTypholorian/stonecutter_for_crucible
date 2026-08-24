package dev.kikugie.stonecutter.data.container

import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import dev.kikugie.stonecutter.build.data.StonecutterBuildConfiguration
import dev.kikugie.stonecutter.controller.flag.FlagScope
import dev.kikugie.stonecutter.controller.flag.StonecutterFlag
import dev.kikugie.stonecutter.controller.flag.StonecutterFlagsImpl
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.ProjectHierarchy.Companion.hierarchy
import dev.kikugie.stonecutter.data.property.StructuredPropertiesExtension
import dev.kikugie.stonecutter.data.tree.ProjectNode
import dev.kikugie.stonecutter.data.tree.ProjectTree
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import dev.kikugie.stonecutter.settings.tree.TreeBuilderImpl
import dev.kikugie.stonecutter.util.propertyKey
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
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

internal abstract class FlagContainer : GradleContainerService {
    private lateinit var settings: ConfigurationRegistry
    private val controller: MutableMap<ProjectHierarchy, ConfigurationRegistry> = HashMap()
    private val node: MutableMap<ProjectHierarchy, ConfigurationRegistry> = HashMap()

    fun settings(provider: StructuredPropertiesExtension): StonecutterFlagsImpl =
        StonecutterFlagsImpl(scope = FlagScope.SETTINGS) { settingsFallback(it, provider) }
            .also { settings = it.registry }

    fun controller(project: ProjectHierarchy, provider: StructuredPropertiesExtension): StonecutterFlagsImpl {
        val settings = settings
        return StonecutterFlagsImpl(scope = FlagScope.CONTROLLER) { controllerFallback(it, settings, provider) }
            .also { controller[project] = it.registry }
    }

    fun node(project: ProjectHierarchy, parent: ProjectHierarchy, provider: StructuredPropertiesExtension): StonecutterFlagsImpl {
        val settings = settings
        val controller = controller[parent]!!
        return StonecutterFlagsImpl(scope = FlagScope.NODE) { nodeFallback(it, controller, settings, provider) }
            .also { node[project] = it.registry }
    }
}

private fun settingsFallback(flag: StonecutterFlag<*>, provider: StructuredPropertiesExtension): String? = when (flag.scope) {
    FlagScope.SETTINGS, FlagScope.LOCAL -> provider.getOrNull(flag.propertyKey)
    else -> null
}

private fun controllerFallback(flag: StonecutterFlag<*>, fallback: ConfigurationRegistry, provider: StructuredPropertiesExtension): String? = when (flag.scope) {
    FlagScope.SETTINGS -> fallback[flag.configuration].toString()
    FlagScope.CONTROLLER, FlagScope.LOCAL -> provider.getOrNull(flag.propertyKey)
    else -> null
}

private fun nodeFallback(flag: StonecutterFlag<*>, fallfront: ConfigurationRegistry, fallback: ConfigurationRegistry, provider: StructuredPropertiesExtension): String? = when (flag.scope) {
    FlagScope.SETTINGS -> fallback[flag.configuration].toString()
    FlagScope.CONTROLLER -> fallfront[flag.configuration].toString()
    else -> provider.getOrNull(flag.propertyKey)
}
