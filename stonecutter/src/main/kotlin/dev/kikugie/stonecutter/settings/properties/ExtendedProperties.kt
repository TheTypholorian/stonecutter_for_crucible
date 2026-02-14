package dev.kikugie.stonecutter.settings.properties

import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlLiteral
import dev.eav.tomlkt.TomlTable
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.startsWith
import kotlinx.serialization.decodeFromString
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.extensions.core.remove
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

internal abstract class ExtendedPropertiesStorage @Inject constructor(val objects: ObjectFactory, val providers: ProviderFactory) {
    protected abstract val container: NamedDomainObjectCollection<ExtendedProjectProperties>

    operator fun get(hierarchy: ProjectHierarchy): Sequence<Pair<String, Any?>> {
        val properties = container.matching { ext ->
            ext.projects.any { it.startsWith(hierarchy) }
        }
        return properties.asSequence().flatMap { it.emit(hierarchy) }
    }

    fun load(file: RegularFile, hierarchy: ProjectHierarchy): ExtendedProjectProperties = synchronized(container) {
        val path = file.asFile.absolutePath
        LOGGER.debug("Loading properties for {} from {}", hierarchy, path)
        val provider = if (path in container.names) container.named(path) {
            projects += hierarchy
        } else loadNew(file, hierarchy).also {
            container.addLater(it)
        }
        return@synchronized provider.get()
    }

    // IDK why is this a provider when it's realized immediately...
    private fun loadNew(file: RegularFile, hierarchy: ProjectHierarchy): Provider<ExtendedProjectProperties> {
        val provider = providers.fileContents(file).asText.map {
            ExtendedProjectProperties(file, hierarchy, Toml.decodeFromString(it))
        }
        return objects.property<ExtendedProjectProperties>().apply {
            set(provider)
            disallowChanges()
            finalizeValueOnRead()
        }
    }

    companion object {
        val DEFAULT_FILENAME = "stonecutter.properties.toml"
        val LOGGER = Logging.getLogger(ExtendedPropertiesStorage::class.java)

        fun Sequence<Pair<String, Any?>>.mergeTo(ext: ExtraPropertiesExtension) {
            for ((key, value) in this)
                if (value == null) ext.remove(key)
                else ext.set(key, value)
        }
    }
}

internal class ExtendedProjectProperties(
    file: RegularFile,
    hierarchy: ProjectHierarchy,
    val table: TomlTable
) : Named {
    val path: String = file.asFile.absolutePath
    val projects: MutableSet<ProjectHierarchy> = mutableSetOf(hierarchy)

    override fun getName(): String = path

    fun emit(target: ProjectHierarchy): Sequence<Pair<String, Any?>> = sequence {
        for (source in projects) if (target.startsWith(source))
            emit(source, target)
    }

    private suspend fun SequenceScope<Pair<String, Any?>>.emit(source: ProjectHierarchy, target: ProjectHierarchy) {
        for ((key, value) in table) when(value) {
            is TomlTable -> {
                val combined = source.resolve(key)
                if (target.startsWith(combined)) emit(combined, target)
                // TODO: report as an error or convert?
            }

            is TomlLiteral ->
                yield(key to value.content)

            else -> {
                // TODO: What to do here too?
            }
        }
    }

    private fun ProjectHierarchy.resolve(key: String) =
        if (key.startsWith(':')) ProjectHierarchy(key) else this + key
}


