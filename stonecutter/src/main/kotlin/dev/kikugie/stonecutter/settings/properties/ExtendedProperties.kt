@file:OptIn(StonecutterExperimentalAPI::class)

package dev.kikugie.stonecutter.settings.properties

import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.deserialization.JsonConverter
import dev.kikugie.stonecutter.data.deserialization.JsonVisitor
import dev.kikugie.stonecutter.data.deserialization.accept
import dev.kikugie.stonecutter.data.startsWith
import kotlinx.serialization.json.JsonArray
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
import java.util.*
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
            obj.accept(JsonPropertyCollector(source, target, tags, ext::set), ArrayDeque())
    }
}

internal class JsonPropertyCollector(
    val segments: List<String>,
    val tags: Set<String>,
    val consumer: (String, String) -> Unit,
) : JsonVisitor<Deque<String>, Unit> {
    constructor(source: ProjectHierarchy, target: ProjectHierarchy, tags: Set<String>, consumer: (String, String) -> Unit)
        : this(source.relativize(target).split(':'), tags, consumer)

    override fun visitObject(obj: JsonObject, data: Deque<String>) {
        for ((k, v) in obj) data.with(k) {
            v.accept(this, it)
        }
    }

    override fun visitArray(arr: JsonArray, data: Deque<String>) {
        JsonPrimitive(arr.toString()).accept(this, data)
    }

    override fun visitPrimitive(prim: JsonPrimitive, data: Deque<String>) {
        val path = resolve(data)
        if (path.isNotEmpty()) consumer(path, prim.content)
    }

    override fun visitNull(data: Deque<String>) {
        // Don't add nulls
    }

    internal fun copy(
        consumer: (String, String) -> Unit = this.consumer,
        tags: Set<String> = this.tags
    ) = JsonPropertyCollector(this.segments, tags, consumer)

    private fun resolve(path: Deque<String>): String = buildString {
        var cursor = 0
        for (element in path) when (element) {
            segments.getOrNull(cursor) -> cursor++
            in tags -> {
                if (cursor > 0) cursor = -1
            }
            else -> {
                append(element); append('.')
                cursor = -1
            }
        }

        if (isNotEmpty()) deleteAt(lastIndex)
    }

    private inline fun <T> Deque<T>.with(element: T, action: (Deque<T>) -> Unit) {
        addLast(element)
        action(this)
        check(removeLast() == element) { "Invalid deque state" }
    }
}
