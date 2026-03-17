@file:OptIn(StonecutterExperimentalAPI::class)

package dev.kikugie.stonecutter.settings.properties

import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.deserialization.JsonConverter
import dev.kikugie.stonecutter.data.resolve
import dev.kikugie.stonecutter.data.startsWith
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    fun load(file: RegularFile, converter: JsonConverter, hierarchy: ProjectHierarchy): ExtendedProjectProperties? = synchronized(container) {
        val path = file.asFile.absolutePath
        LOGGER.debug("Loading properties for {} from {}", hierarchy, path)
        if (path in container.names) container.named(path) { projects += hierarchy }.get()
        else loadNew(file, converter, hierarchy)?.also(container::add)
    }

    private fun loadNew(file: RegularFile, converter: JsonConverter, hierarchy: ProjectHierarchy): ExtendedProjectProperties? {
        val contents = providers.fileContents(file).asText.orNull ?: return null
        val obj = converter.convert(contents)
        return ExtendedProjectProperties(file, hierarchy, obj)
    }

    companion object {
        const val DEFAULT_FILENAME = "stonecutter.properties.toml"
        private val LOGGER = Logging.getLogger(ExtendedPropertiesStorage::class.java)
    }
}

internal class ExtendedProjectProperties(
    file: RegularFile,
    hierarchy: ProjectHierarchy,
    val obj: JsonObject
) : Named {
    val path: String = file.asFile.absolutePath
    val projects: MutableSet<ProjectHierarchy> = mutableSetOf(hierarchy)

    override fun getName(): String = path

    fun apply(target: ProjectHierarchy, ext: ExtraPropertiesExtension, tags: Set<String> = emptySet()) {
        for (source in projects) if (target.startsWith(source))
            JsonVisitor(source, target, tags = tags).visit(obj, ext::set)
    }
}

private data class JsonVisitor(
    val source: ProjectHierarchy,
    val target: ProjectHierarchy,
    val prefix: String = "",
    val tags: Set<String> = emptySet()
) {
    fun visit(obj: JsonObject, consumer: (String, String) -> Unit) {
        for ((k, v) in obj) v.accept(k, consumer)
    }

    private fun visitObject(obj: JsonObject, key: String, consumer: (String, String) -> Unit) {
        val project = source.resolve(prefix + key)
        val copy = when {
            target.startsWith(project) -> copy(source = project)
            tags.isEmpty() -> copy(prefix = "$prefix$key.")
            key in tags -> copy(tags = emptySet())
            else -> null
        }

        copy?.visit(obj, consumer)
    }

    private fun visitPrimitive(prim: JsonPrimitive, key: String, consumer: (String, String) -> Unit) {
        consumer("$prefix$key", prim.content)
    }

    private fun JsonElement.accept(key: String, consumer: (String, String) -> Unit): Unit = when (this) {
        is JsonObject -> visitObject(this, key, consumer)
        is JsonPrimitive -> visitPrimitive(this, key, consumer)
        else -> {
            // do nothing, I guess
        }
    }
}
