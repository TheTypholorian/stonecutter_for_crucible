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
        val collector = JsonPropertyCollector(ext::set)
        for (it in container) it.apply(target, tags, collector)
    }

    fun get(target: ProjectHierarchy, tags: Set<String>, path: List<String>): JsonElement? {
        val retriever = JsonElementRetriever(path)
        return container.findCandidate { it.get(target, tags, retriever) }.element
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

    fun get(target: ProjectHierarchy, tags: Set<String>, retriever: JsonElementRetriever): GetJsonResult = projects.findCandidate {
        if (!target.startsWith(it)) null
        else obj.accept(retriever, JsonPathStack(it.relativize(target).split(':'), tags))
    }

    fun apply(target: ProjectHierarchy, tags: Set<String>, consumer: (String, String) -> Unit) =
        apply(target, tags, JsonPropertyCollector(consumer))

    fun apply(target: ProjectHierarchy, tags: Set<String>, collector: JsonPropertyCollector) {
        for (source in projects) if (target.startsWith(source))
            obj.accept(collector, JsonPathStack(source.relativize(target).split(':'), tags))
    }
}

internal class JsonPropertyCollector(private val consumer: (String, String) -> Unit) : JsonVisitor<JsonPathStack, Unit> {
    override fun visitObject(obj: JsonObject, data: JsonPathStack) {
        for ((k, v) in obj) data.with(k) {
            v.accept(this, it)
        }
    }

    override fun visitArray(arr: JsonArray, data: JsonPathStack) {
        for ((i, e) in arr.withIndex()) data.with(i.toString()) {
            e.accept(this, it)
        }
    }

    override fun visitPrimitive(prim: JsonPrimitive, data: JsonPathStack) {
        val path = data.joinToString()
        if (path.isNotEmpty()) consumer(path, prim.unwrapContent())
    }

    override fun visitNull(data: JsonPathStack) {
        // Don't add nulls
    }
}

internal class JsonElementRetriever(val path: List<String>) : JsonVisitor<JsonPathStack, GetJsonResult> {
    data class GetJsonResult(val depth: Int, val element: JsonElement? = null)

    override fun visitObject(obj: JsonObject, data: JsonPathStack): GetJsonResult = obj.entries.findCandidate(
        GetJsonResult(data.depth, obj.takeIf { data.isValid() })
    ) { (k, v) -> data.with(k) { v.accept(this, it) } }

    override fun visitArray(arr: JsonArray, data: JsonPathStack): GetJsonResult = arr.withIndex().findCandidate(
        GetJsonResult(data.depth, arr.takeIf { data.isValid() })
    ) { (i, e) -> data.with(i.toString()) { e.accept(this, it) } }

    override fun visitPrimitive(prim: JsonPrimitive, data: JsonPathStack): GetJsonResult {
        return GetJsonResult(data.depth, prim.takeIf { data.isValid() })
    }

    override fun visitNull(data: JsonPathStack): GetJsonResult {
        return GetJsonResult(data.depth, JsonNull.takeIf { data.isValid() })
    }

    private fun JsonPathStack.isValid(): Boolean {
        val rem = remainder()
        if (rem.size != path.size) return false
        for ((a, b) in rem.zip(path))
            if (a != b) return false
        return true
    }
}

internal class JsonPathStack(private val project: List<String>, private val tags: Set<String>, internal val stack: Deque<String> = ArrayDeque()) {
    private enum class State { INIT, PATH, TAG, REST }

    private var segments: Int = 0
    private var offset: Int = 0
    private var state: State = State.INIT

    val depth: Int
        inline get() = stack.size

    inline fun <T> with(key: String, action: (JsonPathStack) -> T): T {
        val size = stack.size
        return action(next(key)).also {
            repeat(stack.size - size) { stack.removeLast() }
        }
    }

    fun remainder(): List<String> = stack.drop(offset)
    fun joinToString(): String = remainder().joinToString(".")

    internal fun next(key: String): JsonPathStack = when (state) {
        State.INIT, State.TAG -> when {
            key in tags -> copy(listOf(key), offset = offset + 1, state = State.TAG)
            segments == 0 -> readPath(key)
            else -> rest(key)
        }

        State.PATH -> when {
            key in tags -> copy(listOf(key), offset = offset + 1, state = State.TAG)
            else -> readPath(key)
        }

        State.REST -> rest(key)
    }

    private fun readPath(key: String): JsonPathStack {
        val parts = key.split(':').filterNot(String::isEmpty)
        for ((index, part) in parts.withIndex()){
            val segment = project.getOrNull(segments + index)
            if (segment != part) return rest(key)
        }
        return copy(parts, segments = segments + parts.size, offset = offset + parts.size, state = State.PATH)
    }

    private fun rest(key: String): JsonPathStack = copy(key.split(':').filterNot(String::isEmpty), state = State.REST)

    private fun copy(keys: List<String>, segments: Int = this.segments, offset: Int = this.offset, state: State = this.state): JsonPathStack =
        JsonPathStack(project, tags, stack).apply {
            this.stack.addAll(keys)
            this.segments = segments
            this.offset = offset
            this.state = state
        }
}

internal fun JsonPrimitive.unwrapContent(): String =
    if (isString) content.removeSurrounding("\"") else content

private inline fun <T> Iterable<T>.findCandidate(initial: GetJsonResult = GetJsonResult(0), mapping: (T) -> GetJsonResult?): GetJsonResult {
    var candidate = initial
    for (element in this) {
        val result = mapping(element) ?: continue
        if (candidate.element == null && result.element == null) candidate = result
        else if (result.element != null && result.depth >= candidate.depth) candidate = result
    }
    return candidate
}
