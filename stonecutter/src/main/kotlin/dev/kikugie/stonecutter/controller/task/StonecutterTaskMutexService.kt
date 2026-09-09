package dev.kikugie.stonecutter.controller.task

import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * A no-op build service used to restrict parallel execution of Gradle tasks.
 *
 * Unlike `org.gradle.parallel=false`, applying this service to a task
 * doesn't prevent its dependencies or subsequent tasks from running in parallel.
 */
public interface StonecutterTaskMutexService : BuildService<BuildServiceParameters.None> {
    public companion object {
        /**
         * Registers a mutex service for the given [task].
         *
         * ```kotlin
         * val lock = StonecutterTaskMutexService.create(gradle, "myTask")
         *
         * tasks.named("myTask") {
         *     usesService(lock)
         * }
         * ```
         */
        @JvmStatic public fun create(gradle: Gradle, task: String): Provider<StonecutterTaskMutexService> {
            require(task.isNotBlank()) { "Task name must be non-empty" }
            return gradle.sharedServices.registerIfAbsent("$task-StonecutterMutex", StonecutterTaskMutexService::class.java) {
                maxParallelUsages.set(1)
            }
        }
    }
}