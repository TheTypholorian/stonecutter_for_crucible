package dev.kikugie.stonecutter.settings.properties

import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlElement
import dev.eav.tomlkt.TomlLiteral
import dev.eav.tomlkt.TomlTable
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.resolve
import dev.kikugie.stonecutter.data.startsWith
import kotlinx.serialization.decodeFromString
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.namedDomainObjectList
import javax.inject.Inject

internal abstract class ExtendedPropertiesStorage @Inject constructor(val objects: ObjectFactory, val providers: ProviderFactory) {
    private val container: NamedDomainObjectCollection<ExtendedProjectProperties> = objects.namedDomainObjectList(ExtendedProjectProperties::class)

    fun apply(target: ProjectHierarchy, ext: ExtraPropertiesExtension, tags: Set<String> = emptySet()) {
        for (it in container) it.apply(target, ext, tags)
    }

    fun load(file: RegularFile, hierarchy: ProjectHierarchy): ExtendedProjectProperties? = synchronized(container) {
        val path = file.asFile.absolutePath
        LOGGER.debug("Loading properties for {} from {}", hierarchy, path)
        if (path in container.names) container.named(path) { projects += hierarchy }.get()
        else loadNew(file, hierarchy)?.also(container::add)
    }

    private fun loadNew(file: RegularFile, hierarchy: ProjectHierarchy): ExtendedProjectProperties? =
        providers.fileContents(file).asText.map { ExtendedProjectProperties(file, hierarchy, Toml.decodeFromString(it)) }.orNull

    companion object {
        const val DEFAULT_FILENAME = "stonecutter.properties.toml"
        private val LOGGER = Logging.getLogger(ExtendedPropertiesStorage::class.java)
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

    fun apply(target: ProjectHierarchy, ext: ExtraPropertiesExtension, tags: Set<String> = emptySet()) {
        for (source in projects) if (target.startsWith(source))
            TomlVisitor(source, target, tags = tags).visitTable(table) { k, v -> ext[k] = v }
    }
}

private data class TomlVisitor(
    val source: ProjectHierarchy,
    val target: ProjectHierarchy,
    val prefix: String = "",
    val tags: Set<String> = emptySet()
) {
    fun visitTable(table: TomlTable, consumer: (String, String) -> Unit) {
        for ((key, value) in table) visitEntry(key, value, consumer)
    }

    private fun visitEntry(key: String, value: TomlElement, consumer: (String, String) -> Unit): Unit = when(value) {
        is TomlTable -> {
            val project = source.resolve(prefix + key)
            val copy = when {
                target.startsWith(project) -> copy(source = project)
                tags.isEmpty() -> copy(prefix = "$prefix$key.")
                key in tags -> copy(tags = emptySet())
                else -> null
            }
            copy?.visitTable(value, consumer) ?: Unit
        }

        is TomlLiteral -> {
            consumer("$prefix$key", value.content)
        }

        else -> {
        }
    }
}
