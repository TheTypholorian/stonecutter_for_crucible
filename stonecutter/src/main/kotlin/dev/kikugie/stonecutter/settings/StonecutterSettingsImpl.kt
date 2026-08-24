package dev.kikugie.stonecutter.settings

import dev.kikugie.semver.data.Version
import dev.kikugie.stonecutter.ProjectReference
import dev.kikugie.stonecutter.StonecutterPlugin
import dev.kikugie.stonecutter.build.task.StonecutterErrorsService
import dev.kikugie.stonecutter.controller.StonecutterControllerManager
import dev.kikugie.stonecutter.controller.file.FileHandlerContainer
import dev.kikugie.stonecutter.controller.file.FileHandlerService
import dev.kikugie.stonecutter.controller.flag.FlagScope
import dev.kikugie.stonecutter.controller.flag.StonecutterFlag
import dev.kikugie.stonecutter.controller.flag.StonecutterFlagsImpl
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.controller.file.configureDefaults
import dev.kikugie.stonecutter.data.container.FlagContainer
import dev.kikugie.stonecutter.data.container.GradleContainerService.Companion.createContainer
import dev.kikugie.stonecutter.data.container.GradleContainerService.Companion.getContainer
import dev.kikugie.stonecutter.data.container.ProjectNodeContainer
import dev.kikugie.stonecutter.data.container.TreeBuilderContainer
import dev.kikugie.stonecutter.data.property.StructuredPropertiesExtension
import dev.kikugie.stonecutter.data.property.StructuredPropertiesImpl
import dev.kikugie.stonecutter.data.version.LenientOperations
import dev.kikugie.stonecutter.data.version.VersionOperations
import dev.kikugie.stonecutter.settings.task.StonecutterIdeaConfigTask
import dev.kikugie.stonecutter.settings.tree.TreeBuilder
import dev.kikugie.stonecutter.settings.tree.TreeBuilderImpl
import dev.kikugie.stonecutter.util.isIdeaSync
import dev.kikugie.stonecutter.util.newInstance
import dev.kikugie.stonecutter.util.projectDirectory
import dev.kikugie.stonecutter.util.propertyKey
import dev.kikugie.stonecutter.util.requestTasks
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.build.event.BuildEventsListenerRegistry
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
    internal val treeBuilderContainer: TreeBuilderContainer = settings.gradle.createContainer<TreeBuilderContainer>().get()
    override val properties: StructuredPropertiesImpl = setupDefaultProperties(settings, objects)
    override val flags: StonecutterFlagsImpl = settings.gradle.createContainer<FlagContainer>().get()
        .settings(properties)
    private var usesGroovy: Boolean = false

    @get:Inject
    internal abstract val buildFeatures: BuildFeatures

    init {
        properties.configureExport(flags[StonecutterFlag.EXPOSE_PROPERTIES], settings.extra)

        LOGGER.lifecycle("Running Stonecutter ${StonecutterPlugin.VERSION}")
        if (buildFeatures.isolatedProjects.active.get()) LOGGER.reportIsolatedProjectsComplaint()
        // This is the ugliest configuration block I have
        with(settings.gradle) {
            createContainer<ProjectNodeContainer>()
            val handlers = createContainer<FileHandlerContainer>().get()
            handlers.configureDefaults()
            configureSyncTask()
            configureServices(handlers, registry)
            settingsEvaluated {
                if (usesGroovy && !flags[StonecutterFlag.HARD_MODE]) LOGGER.reportGroovyComplaint()
            }
        }
    }

    override fun create(ref: ProjectReference, builder: TreeBuilder) {
        builder as TreeBuilderImpl

        builder.build(this, ref)
        usesGroovy = usesGroovy || checkGroovyUsage(builder)
    }

    private companion object {
        val LOGGER: Logger = Logging.getLogger("StonecutterSettings")
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

private fun Gradle.configureServices(handlers: FileHandlerContainer, registry: BuildEventsListenerRegistry) {
    handlers.service = sharedServices.registerIfAbsent(FileHandlerService.NAME, FileHandlerService::class)
    projectsEvaluated {
        @Suppress("UNCHECKED_CAST")
        val params = checkNotNull(sharedServices.registrations.findByName(FileHandlerService.NAME))
            .parameters as FileHandlerService.Parameters
        params.handlers.value(handlers.build()).finalizeValue()

        sharedServices.registerIfAbsent(StonecutterErrorsService.NAME, StonecutterErrorsService::class) {
            for (node in gradle.getContainer<ProjectNodeContainer>().projects)
                parameters.active.put(node.hierarchy.path, node.metadata.isActive)
        }.let(registry::onTaskCompletion)
    }
}

@Suppress("UnstableApiUsage")
private fun setupDefaultProperties(settings: Settings, objects: ObjectFactory): StructuredPropertiesImpl = objects.newInstance(settings.providers) {
    loadDefaults(settings.layout.settingsDirectory)
    holder.extraProperties = settings.extra
    holder.reload()
}

private fun checkGroovyUsage(tree: TreeBuilderImpl): Boolean {
    if (tree.controller is StonecutterControllerManager.Groovy) return true
    for (branch in tree.branchBuilders.values)
        for (node in branch.versions)
            if (node.buildscript.endsWith(".gradle")) return true
    return false
}

private fun Logger.reportIsolatedProjectsComplaint(): Unit = warn(
    """
    Warning: Stonecutter is used with Isolated Projects enabled
    ------------------------------------------------------------------------------------
    Isolated Projects support is experimental. 
    Stonecutter relies on cross-project configuration in some places, 
    so some features may not behave correctly under this mode.
    Please report any issues to https://codeberg.org/stonecutter/stonecutter/issues
    ------------------------------------------------------------------------------------
    """.trimIndent()
)

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