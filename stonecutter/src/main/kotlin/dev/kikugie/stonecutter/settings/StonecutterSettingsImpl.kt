package dev.kikugie.stonecutter.settings

import dev.kikugie.commons.takeAs
import dev.kikugie.semver.data.Version
import dev.kikugie.stonecutter.ProjectReference
import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import dev.kikugie.stonecutter.StonecutterPlugin
import dev.kikugie.stonecutter.build.data.BuildConfigurationContainer
import dev.kikugie.stonecutter.build.task.StonecutterErrorsService
import dev.kikugie.stonecutter.controller.file.FileHandlerContainer
import dev.kikugie.stonecutter.controller.file.FileHandlerService
import dev.kikugie.stonecutter.controller.file.configureDefaults
import dev.kikugie.stonecutter.controller.tree.ProjectNodeContainer
import dev.kikugie.stonecutter.data.ProjectHierarchy.Companion.hierarchy
import dev.kikugie.stonecutter.data.container.GradleContainerExtension.Companion.createContainer
import dev.kikugie.stonecutter.data.container.GradleContainerExtension.Companion.getContainer
import dev.kikugie.stonecutter.data.version.LenientOperations
import dev.kikugie.stonecutter.data.version.VersionOperations
import dev.kikugie.stonecutter.settings.properties.ExtendedPropertiesStorage
import dev.kikugie.stonecutter.settings.properties.ExtendedPropertyConfigImpl
import dev.kikugie.stonecutter.settings.properties.ExtendedPropertyProvider
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
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.registerIfAbsent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.getValue

@OptIn(StonecutterExperimentalAPI::class)
internal abstract class StonecutterSettingsImpl @Inject constructor(
    internal val settings: Settings,
    registry: BuildEventsListenerRegistry,
    objects: ObjectFactory,
) : StonecutterSettingsExtension(objects), VersionOperations<Version> by LenientOperations {
    override val properties: ExtendedPropertyProvider.ExtendedPropertyConfig = setupDefaultProperties(settings, objects)

    internal val treeBuilderContainer: TreeBuilderContainer = settings.gradle.createContainer()
    internal val isHardMode: Boolean by lazy {
        settings.providers.gradleProperty("dev.kikugie.stonecutter.hard_mode").getOrElse("false").toBoolean()
    }
    private var usesGroovy: Boolean = false


    init {
        LOGGER.lifecycle("Running Stonecutter ${StonecutterPlugin.VERSION}")
        // This is the ugliest configuration block I have
        with(settings.gradle) {
            createContainer<ProjectNodeContainer>()
            createContainer<BuildConfigurationContainer>()
            settingsEvaluated {
                if (usesGroovy && !isHardMode) LOGGER.reportGroovyComplaint()
            }
            projectsLoaded {
                createIdeaTask(this)
            }

            val handlers = createContainer<FileHandlerContainer>()
            handlers.configureDefaults()
            projectsEvaluated {
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
        }
    }

    override fun create(ref: ProjectReference, builder: TreeBuilder) =
        builder.takeAs<TreeBuilderImpl>().createWith(this, ref)

    internal fun checkGroovy(file: String): String = file.also {
        usesGroovy = usesGroovy || it.endsWith(".gradle")
    }

    private companion object {
        val LOGGER = Logging.getLogger("StonecutterSettings")
    }
}

@OptIn(StonecutterExperimentalAPI::class) @Suppress("UnstableApiUsage")
private fun setupDefaultProperties(settings: Settings, objects: ObjectFactory): ExtendedPropertyProvider.ExtendedPropertyConfig = with(settings) {
    val hierarchy = rootProject.hierarchy
    val ext = gradle.extensions.create<ExtendedPropertiesStorage>("ExtendedProperties")
    ext.load(layout.settingsDirectory.file(ExtendedPropertiesStorage.DEFAULT_FILENAME), hierarchy)
        ?.apply(hierarchy, extra)
    ExtendedPropertyConfigImpl(hierarchy, extra, ext, objects)
}

private fun createIdeaTask(gradle: Gradle) {
    val root = gradle.rootProject
    val task = root.tasks.register<StonecutterIdeaConfigTask>("stonecutterIdea") {
        group = "stonecutter-impl"
        description = "Generates IntelliJ IDEA run configurations for version switch tasks"

        projects.set(ConcurrentHashMap())
        configurations.from(root.projectDirectory.resolve(".idea/runConfigurations"))
            .include { it.file.name.startsWith("Stonecutter") }
    }
    if (isIdeaSync)
        gradle.requestTasks(listOf(task.name), root.path, root.projectDir)
}

private fun Logger.reportGroovyComplaint(): Unit = warn(
    """
    NOTICE: Limited Groovy DSL support for Stonecutter
    
    While functional, the plugin's features are limited 
    by the Groovy syntax and it has reduced IDE support.
    For the best experience, including enhanced syntax, autocompletion, 
    documentation lookup and debugging, it's recommended to use Kotlin DSL.
    
    For more information see: 
    - https://stonecutter.codeberg.page/wiki/faq#groovy-support
    """.trimIndent()
)