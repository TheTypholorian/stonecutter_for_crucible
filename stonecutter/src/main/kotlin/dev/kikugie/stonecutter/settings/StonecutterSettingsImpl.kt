@file:OptIn(StonecutterInternalAPI::class)

package dev.kikugie.stonecutter.settings

import dev.kikugie.commons.takeAs
import dev.kikugie.semver.data.Version
import dev.kikugie.stonecutter.ProjectReference
import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.StonecutterPlugin
import dev.kikugie.stonecutter.build.data.BuildConfigurationContainer
import dev.kikugie.stonecutter.build.task.StonecutterErrorsService
import dev.kikugie.stonecutter.controller.StonecutterControllerManager
import dev.kikugie.stonecutter.controller.file.FileHandlerContainer
import dev.kikugie.stonecutter.controller.file.FileHandlerService
import dev.kikugie.stonecutter.controller.file.configureDefaults
import dev.kikugie.stonecutter.controller.tree.ProjectNodeContainer
import dev.kikugie.stonecutter.data.ProjectHierarchy.Companion.hierarchy
import dev.kikugie.stonecutter.data.container.GradleContainerExtension.Companion.createContainer
import dev.kikugie.stonecutter.data.container.GradleContainerExtension.Companion.getContainer
import dev.kikugie.stonecutter.data.version.LenientOperations
import dev.kikugie.stonecutter.data.version.VersionOperations
import dev.kikugie.stonecutter.settings.properties.StructuredPropertyConfigImpl
import dev.kikugie.stonecutter.settings.properties.StructuredPropertiesLoader
import dev.kikugie.stonecutter.settings.properties.StructuredPropertiesProvider
import dev.kikugie.stonecutter.settings.task.StonecutterIdeaConfigTask
import dev.kikugie.stonecutter.settings.tree.TreeBuilder
import dev.kikugie.stonecutter.settings.tree.TreeBuilderContainer
import dev.kikugie.stonecutter.settings.tree.TreeBuilderImpl
import dev.kikugie.stonecutter.util.isIdeaSync
import dev.kikugie.stonecutter.util.projectDirectory
import dev.kikugie.stonecutter.util.requestTasks
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.registerIfAbsent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

internal abstract class StonecutterSettingsImpl @Inject constructor(
    settings: Settings,
    registry: BuildEventsListenerRegistry,
    objects: ObjectFactory,
) : StonecutterSettingsExtension(settings, objects), VersionOperations<Version> by LenientOperations {
    override val properties: StructuredPropertiesProvider.Config = setupDefaultProperties(settings, objects)
    override val isHardMode: Boolean by lazy {
        settings.providers.gradleProperty("dev.kikugie.stonecutter.hard_mode").getOrElse("false").toBoolean()
    }

    internal val treeBuilderContainer: TreeBuilderContainer = settings.gradle.createContainer()
    private var usesGroovy: Boolean = false


    init {
        LOGGER.lifecycle("Running Stonecutter ${StonecutterPlugin.VERSION}")
        // This is the ugliest configuration block I have
        with(settings.gradle) {
            createContainer<ProjectNodeContainer>()
            createContainer<BuildConfigurationContainer>()
            val handlers = createContainer<FileHandlerContainer>()
            handlers.configureDefaults()
            configureSyncTask()
            configureServices(handlers, registry)
            settingsEvaluated {
                if (usesGroovy && !isHardMode) LOGGER.reportGroovyComplaint()
            }
        }
    }

    override fun create(ref: ProjectReference, builder: TreeBuilder) {
        builder as TreeBuilderImpl

        builder.build(this, ref)
        usesGroovy = usesGroovy || checkGroovyUsage(builder)
    }

    private companion object {
        val LOGGER = Logging.getLogger("StonecutterSettings")
    }
}

private fun Gradle.configureSyncTask() {
    projectsLoaded {
        val root = rootProject
        val task = root.tasks.register<StonecutterIdeaConfigTask>("stonecutterIdea") {
            group = "stonecutter-impl"
            description = "Generates IntelliJ IDEA run configurations for version switch tasks"

            projects.set(ConcurrentHashMap())
            configurations.from(root.projectDirectory.resolve(".idea/runConfigurations"))
                .include { it.file.name.startsWith("Stonecutter") }
        }
        if (isIdeaSync)
            requestTasks(listOf(task.name), root.path, root.projectDir)
    }
}

private fun Gradle.configureServices(handlers: FileHandlerContainer, registry: BuildEventsListenerRegistry) = projectsEvaluated {
    sharedServices.registerIfAbsent(FileHandlerService.NAME, FileHandlerService::class) {
        parameters.handlers.value(handlers.build()).finalizeValue()
    }
    sharedServices.registerIfAbsent(StonecutterErrorsService.NAME, StonecutterErrorsService::class) {
        val trees = gradle.getContainer<ProjectNodeContainer>().values.asSequence().map { it.tree }.toSet()
        for (tree in trees) {
            val active = tree.current ?: tree.versions.first()
            for (node in tree.nodes) parameters.active.put(node.hierarchy.toString(), node.metadata == active)
        }
    }.let(registry::onTaskCompletion)
}

@OptIn(StonecutterExperimentalAPI::class) @Suppress("UnstableApiUsage")
private fun setupDefaultProperties(settings: Settings, objects: ObjectFactory): StructuredPropertiesProvider.Config {
    val loader: StructuredPropertiesLoader = settings.gradle.extensions.create("StructuredProperties")
    return StructuredPropertyConfigImpl(settings.rootProject.hierarchy, settings.extra, loader, objects)
        .apply { loadDefaults(settings.layout.settingsDirectory) }
}

private fun checkGroovyUsage(tree: TreeBuilderImpl): Boolean {
    if (tree.controller is StonecutterControllerManager.Groovy) return true
    for (branch in tree.branchBuilders.values)
        for (node in branch.versions)
            if (node.buildscript.endsWith(".gradle")) return true
    return false
}

private fun Logger.reportGroovyComplaint(): Unit = warn(
    """
    Warning: Stonecutter is used with Groovy buildscripts
    ------------------------------------------------------------------------------------
    Stonecutter requires a more complex Gradle setup than typical Java projects.
    While Groovy DSL works, Kotlin DSL offers significant advantages:
    - The type-safe model prevents silent failures and Groovy's dynamic typing
      shenanigans that are hell to debug.
    - Full IDE support with code completion, documentation and source navigation.
    - Kotlin-exclusive operator functions for simpler configurations.
    ------------------------------------------------------------------------------------
    For more information see:
    - https://stonecutter.kikugie.dev/wiki/faq#groovy-support
    - https://docs.gradle.org/current/userguide/migrating_from_groovy_to_kotlin_dsl.html
    
    To acknowledge and disable this warning add to the `gradle.properties`:
    ```
    dev.kikugie.stonecutter.hard_mode=true
    ```
    ------------------------------------------------------------------------------------
    """.trimIndent()
)