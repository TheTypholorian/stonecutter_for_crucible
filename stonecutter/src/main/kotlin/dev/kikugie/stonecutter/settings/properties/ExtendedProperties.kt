@file:OptIn(StonecutterExperimentalAPI::class)

package dev.kikugie.stonecutter.settings.properties

import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.deserialization.JsonConverter
import dev.kikugie.stonecutter.data.deserialization.JsonVisitor
import dev.kikugie.stonecutter.data.deserialization.accept
import dev.kikugie.stonecutter.data.startsWith
import dev.kikugie.stonecutter.settings.properties.JsonElementRetriever.GetJsonResult
import kotlinx.serialization.json.*
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

    fun get(target: ProjectHierarchy, tags: Set<String>, path: List<String>): JsonElement? {
        val result = container.findCandidate { it.get(target, tags, path) }
        return result.element
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

    fun get(target: ProjectHierarchy, tags: Set<String>, path: List<String>): GetJsonResult = projects.findCandidate {
        if (!target.startsWith(it)) null
        else obj.accept(JsonElementRetriever(it, target, tags, path), ArrayDeque())
    }

    fun apply(target: ProjectHierarchy, ext: ExtraPropertiesExtension, tags: Set<String> = emptySet()) {
        for (source in projects) if (target.startsWith(source))
            obj.accept(JsonPropertyCollector(source, target, tags, ext::set), ArrayDeque())
    }
}

internal class JsonPropertyCollector(val segments: List<String>, val tags: Set<String>, val consumer: (String, String) -> Unit) :
    JsonVisitor<Deque<String>, Unit> {
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
        val path = data.toPath()
        if (path.isNotEmpty()) consumer(path, prim.unwrapContent())
    }

    override fun visitNull(data: Deque<String>) {
        // Don't add nulls
    }

    private fun Deque<String>.toPath(): String = buildString {
        var cursor = 0
        for (element in this@toPath) when (element) {
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
}

internal class JsonElementRetriever(val segments: List<String>, val tags: Set<String>, val path: List<String>) :
    JsonVisitor<Deque<String>, GetJsonResult> {
    constructor(source: ProjectHierarchy, target: ProjectHierarchy, tags: Set<String>, path: List<String>)
        : this(source.relativize(target).split(':'), tags, path)

    data class GetJsonResult(val depth: Int, val element: JsonElement? = null)

    override fun visitObject(obj: JsonObject, data: Deque<String>): GetJsonResult = obj.entries.findCandidate(
        GetJsonResult(data.lastIndex, obj.takeIf { data.hasArrived() })
    ) { (k, v) -> data.with(k) { v.accept(this, it) } }

    override fun visitArray(arr: JsonArray, data: Deque<String>): GetJsonResult = arr.withIndex().findCandidate(
        GetJsonResult(data.lastIndex, arr.takeIf { data.hasArrived() })
    ) { (i, e) -> data.with(i.toString()) { e.accept(this, it) } }

    override fun visitPrimitive(prim: JsonPrimitive, data: Deque<String>): GetJsonResult {
        return GetJsonResult(data.lastIndex, prim.takeIf { data.hasArrived() })
    }

    override fun visitNull(data: Deque<String>): GetJsonResult {
        return GetJsonResult(data.lastIndex, JsonNull.takeIf { data.hasArrived() })
    }

    private fun Deque<String>.hasArrived(): Boolean {
        var count = 0
        var cursor = 0
        var state = ProgressState.INIT_WALK
        for (element in this) when(element.also { count++ }) {
            in tags if (state == ProgressState.INIT_WALK) -> {
                if (cursor > 0) cursor = -1
                count--
            }

            segments.getOrNull(cursor) if (state == ProgressState.INIT_WALK) -> {
                cursor++
                count--
            }

            path.first() if (state == ProgressState.INIT_WALK) -> {
                state = ProgressState.PATH_WALK
                cursor = 1
            }

            path.getOrNull(cursor) if (state == ProgressState.PATH_WALK) -> {
                cursor++
            }
        }

        return state == ProgressState.PATH_WALK && count == path.size
    }

    private enum class ProgressState {
        INIT_WALK, PATH_WALK
    }
}

internal fun JsonPrimitive.unwrapContent(): String =
    if (isString) content.removeSurrounding("\"") else content

private inline val Deque<*>.lastIndex: Int
    get() = size - 1

private inline fun <T, R> Deque<T>.with(element: T, action: (Deque<T>) -> R): R {
    addLast(element)
    return action(this).also { check(removeLast() == element) { "Invalid deque state" } }
}

private inline fun <T> Iterable<T>.findCandidate(initial: GetJsonResult = GetJsonResult(0), mapping: (T) -> GetJsonResult?): GetJsonResult {
    var candidate = initial
    for (element in this) {
        val result = mapping(element) ?: continue
        if (candidate.element == null && result.element == null) candidate = result
        else if (result.element != null && result.depth >= candidate.depth) candidate = result
    }
    return candidate
}
