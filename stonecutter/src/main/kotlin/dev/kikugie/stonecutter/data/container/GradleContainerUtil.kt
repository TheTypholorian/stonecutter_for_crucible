package dev.kikugie.stonecutter.data.container

import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.kotlin.dsl.registerIfAbsent

internal inline fun <reified T : GradleContainerService> Gradle.createContainer(): Provider<T> =
    sharedServices.registerIfAbsent(requireNotNull(T::class.simpleName) { "Provided class has no name" }, T::class)

internal inline fun <reified T : GradleContainerService> Gradle.getContainer(): T {
    val name: String = requireNotNull(T::class.simpleName) { "Provided class has no name" }
    val service: BuildServiceRegistration<*, *> = checkNotNull(sharedServices.registrations.findByName(name)) { "No service registered for name $name" }
    return service.service.get() as T
}
