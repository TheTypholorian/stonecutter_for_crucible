package dev.kikugie.stonecutter.data.container

import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.tree.ProjectNode
import dev.kikugie.stonecutter.data.tree.ProjectTree
import dev.kikugie.stonecutter.settings.tree.TreeBuilderImpl
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.namedDomainObjectSet
import javax.inject.Inject
import kotlin.reflect.KClass

/**Represents a Gradle-level extension used for storing per-project data.*/
internal abstract class GradleContainerExtension<T : Named> protected constructor(objects: ObjectFactory, type: KClass<T>) {
    val collection: NamedDomainObjectSet<T> = objects.namedDomainObjectSet(type)

    operator fun get(name: String): T? = synchronized(collection) {
        collection.findByName(name)
    }

    operator fun plusAssign(value: T): Unit = synchronized(collection) {
        collection.add(value)
    }

    companion object {
        inline fun <reified T : Any> Gradle.createContainer(): T =
            extensions.create<T>(requireNotNull(T::class.simpleName) { "Provided class has no name" })

        inline fun <reified T : Any> Gradle.createContainer(vararg args: Any): T =
            extensions.create<T>(requireNotNull(T::class.simpleName) { "Provided class has no name" }, *args)

        inline fun <reified T : Any> Gradle.getContainer(): T =
            extensions.getByType<T>()
    }
}

internal open class TreeBuilderContainer @Inject constructor(objects: ObjectFactory)
    : GradleContainerExtension<TreeBuilderImpl>(objects, TreeBuilderImpl::class) {
    operator fun get(hierarchy: ProjectHierarchy): TreeBuilderImpl? =
        get("TreeBuilderImpl@$hierarchy")
}

internal open class ProjectNodeContainer @Inject constructor(objects: ObjectFactory)
    : GradleContainerExtension<ProjectNode>(objects, ProjectNode::class) {

    operator fun get(hierarchy: ProjectHierarchy): ProjectNode? =
        get("ProjectNode@$hierarchy")

    operator fun plusAssign(tree: ProjectTree): Unit = synchronized(collection) {
        for (node in tree.nodes) collection.add(node)
    }
}
