package dev.kikugie.stonecutter.controller.task

import dev.kikugie.commons.takeAs
import dev.kikugie.stonecutter.data.collection.TaskProviderMap
import dev.kikugie.stonecutter.data.tree.ProjectNode
import org.gradle.api.Task
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.TaskProvider
import kotlin.reflect.KClass

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class ControllerTasksDsl

private typealias ProjectFilter = (ProjectNode.() -> Boolean)
private const val NAMED_DEPRECATION: String = "Incompatible with isolated projects. Use 'names()' to get task paths instead."
private val ALWAYS_TRUE: (Any) -> Boolean = { true }

/**
 * Extension class providing access to [StonecutterControllerExtension][dev.kikugie.stonecutter.controller.StonecutterControllerExtension]
 * tasks and aggregation utilities.
 *
 * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-controller#task-hooks">Task Hooks Documentation</a>
 */
@ControllerTasksDsl
public abstract class StonecutterControllerTasks {
    /** The default node ordering used in [order], sorting by [StonecutterProject.parsed][dev.kikugie.stonecutter.data.StonecutterProject.parsed]. */
    public val versionComparator: Comparator<ProjectNode> get() = VERSION_COMPARATOR

    /**
     * All `stonecutterSwitchTo..` tasks.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-controller#lifecycle">Task Lifecycle Documentation</a>
     */
    public abstract val switch: TaskProviderMap<StonecutterSwitchTask>

    /**
     * Returns a lazily evaluated collection of tasks in nodes with the given [name].
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-controller#aggregation">Task Aggregation Documentation</a>
     */
    @Deprecated(NAMED_DEPRECATION, ReplaceWith("names(name, filter)"))
    @JvmOverloads
    public fun named(name: String, filter: ProjectFilter = ALWAYS_TRUE): MapProperty<ProjectNode, TaskProvider<*>> =
        namedImpl(name, null, filter)

    /** Returns a lazily evaluated collection of tasks in nodes with the given [name] and [cls]. */
    @Deprecated(NAMED_DEPRECATION, ReplaceWith("names(name, filter)"))
    @JvmOverloads
    public fun <T : Task> named(name: String, cls: Class<T>, filter: ProjectFilter = ALWAYS_TRUE): MapProperty<ProjectNode, TaskProvider<T>> =
        namedImpl(name, cls, filter).takeAs()

    /** Returns a lazily evaluated collection of tasks in nodes with the given [name] and [cls]. */
    @Deprecated(NAMED_DEPRECATION, ReplaceWith("names(name, filter)"))
    @JvmOverloads
    public fun <T : Task> named(name: String, cls: KClass<T>, filter: ProjectFilter = ALWAYS_TRUE): MapProperty<ProjectNode, TaskProvider<T>> =
        namedImpl(name, cls.java, filter).takeAs()

    /** Returns a lazily evaluated collection of tasks in nodes with the given [name] and [T] class. */
    @Deprecated(NAMED_DEPRECATION, ReplaceWith("names(name, filter)"))
    @JvmOverloads @JvmName("reifiedNamed")
    public inline fun <reified T : Task> named(name: String, noinline filter: ProjectFilter = { true }): MapProperty<ProjectNode, TaskProvider<T>> =
        @Suppress("DEPRECATION") named(name, T::class.java, filter).takeAs()

    /** Returns task paths for the [task][name] for all [ProjectNode]s matching the [filter]. */
    @JvmOverloads
    public fun names(name: String, filter: ProjectFilter = ALWAYS_TRUE): MapProperty<ProjectNode, String> =
        namesImpl(name, filter)

    /**
     * Forces the tasks in nodes with the given [name] to execute with the given [ordering].
     *
     * Important considerations:
     * - This function must be called at most once per task name.
     * Subsequent calls may result in undefined behaviour or a cyclical dependency.
     * - The targeted task must be an endpoint task, otherwise the function will have no effect.
     * For example, `publishMods` is not an endpoint task as it just calls `publishModrinth`
     * and `publishCurseforge`, which should be ordered instead.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-controller#ordering">Task Ordering Documentation</a>
     */
    @JvmOverloads
    public fun order(name: String, ordering: Comparator<ProjectNode> = versionComparator, filter: ProjectFilter = ALWAYS_TRUE): Unit =
        orderImpl(name, ordering, filter)

    protected abstract fun namedImpl(name: String, cls: Class<out Task>?, filter: ProjectFilter): MapProperty<ProjectNode, TaskProvider<*>>

    protected abstract fun namesImpl(name: String, filter: ProjectFilter): MapProperty<ProjectNode, String>

    protected abstract fun orderImpl(name: String, ordering: Comparator<ProjectNode>, filter: ProjectFilter)

    private companion object {
        /**The default node ordering used in [order], sorting by [StonecutterProject.parsed][dev.kikugie.stonecutter.data.StonecutterProject.parsed].*/
        @JvmField val VERSION_COMPARATOR: Comparator<ProjectNode> =
            Comparator.comparing { it.metadata.parsed }
    }
}
