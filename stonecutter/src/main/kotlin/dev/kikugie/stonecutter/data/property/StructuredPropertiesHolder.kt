package dev.kikugie.stonecutter.data.property

import dev.kikugie.stonecutter.data.deserialization.*
import dev.kikugie.stonecutter.data.property.StructuredPropertiesHolder.OverwritePolicy.*
import org.gradle.api.plugins.ExtraPropertiesExtension
import java.io.File

public class StructuredPropertiesHolder {
    private val appliedTags: HashSet<String> = HashSet()
    private val loadedFiles: HashSet<File> = HashSet()
    private val loadedStructs: ArrayList<SCMap> = ArrayList()
    private val lookupCache: HashMap<String, List<SCElement>> = HashMap()

    private var overwritePolicy: OverwritePolicy = MERGE
    private var parsedMap: SCMap = SCMap(emptyMap())
    internal var extraProperties: ExtraPropertiesExtension? = null

    public fun all(): SCMap = parsedMap

    public fun get(path: String, single: Boolean = false): List<SCElement> =
        lookupCache.getOrPut(path) { ArrayList<SCElement>().apply { collect(parsedMap, path, single) } }

    public fun tags(tags: Iterable<String>, reload: Boolean = true): Boolean = appliedTags
        .addAll(tags)
        .also { if (it && reload) reload() }

    public fun load(file: File?, reload: Boolean = true, data: () -> SCMap?): Boolean {
        if (file != null && file in loadedFiles) return false

        loadedStructs += data() ?: return false
        if (file != null) loadedFiles += file

        if (reload) reload()
        return true
    }

    public fun overwrite(policy: OverwritePolicy) {
        if (policy == overwritePolicy) return

        overwritePolicy = policy
        reload()
    }

    internal fun reload(): Unit = with(SCMapBuilder()) {
        val merger = StructuredPropertiesMerger(overwritePolicy, this)
        for (struct in loadedStructs) for ((key, value) in struct)
            value.accept(merger, key)
        lookupCache.clear()
        parsedMap = applyTags(build())
        extraProperties?.let(::applyToExtra)
    }

    private fun applyToExtra(ext: ExtraPropertiesExtension, map: SCMap = parsedMap, prefix: String = "") {
        for ((key, value) in map) {
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            when (value) {
                is SCMap -> applyToExtra(ext, value, path)
                is SCPrimitive -> ext[path] = value.toString()
                else -> Unit // lists and nulls are not exposed as flat entries
            }
        }
    }

    private fun applyTags(root: SCMap): SCMap = when {
        appliedTags.isEmpty() -> root
        else -> with(SCMapBuilder()) {
            val segments = appliedTags.mapTo(HashSet()) { it.split(':') }
            val merger = StructuredPropertiesTagger(segments, overwritePolicy, this)
            root.accept(merger, emptyList())
            build()
        }
    }

    private fun MutableList<SCElement>.collect(map: SCMap, path: String, single: Boolean) {
        for ((key, value) in map) when {
            key == path ->
                add(value).also { if (single) break }

            value is SCMap && path.startsWith("$key.") ->
                collect(value, path.substring(key.length + 1), single)
        }
    }

    public enum class OverwritePolicy {
        KEEP, MERGE, WRITE,
    }
}

private class StructuredPropertiesMerger(
    val policy: StructuredPropertiesHolder.OverwritePolicy,
    val builder: SCMapBuilder
) : SCElement.Visitor<String, Unit> {
    override fun visitNull(ctx: String) {
        builder[ctx, policy != KEEP] = Null
    }

    override fun visitPrimitive(prim: SCPrimitive, ctx: String) {
        builder[ctx, policy != KEEP] = primitive(prim.content)
    }

    override fun visitList(list: SCList, ctx: String) = when (policy) {
        KEEP -> builder.computeIfAbsent(ctx) { processList(list) }.let { }
        WRITE -> builder[ctx] = processList(list)
        MERGE -> when (val it = builder[ctx]) {
            is SCListBuilder -> processList(list, it).let { }
            else -> builder[ctx] = processList(list)
        }
    }

    override fun visitMap(map: SCMap, ctx: String) = when (policy) {
        KEEP -> builder.computeIfAbsent(ctx) { processMap(map) }.let { }
        WRITE -> builder[ctx] = processMap(map)
        MERGE -> when (val it = builder[ctx]) {
            is SCMapBuilder -> processMap(map, it).let { }
            else -> builder[ctx] = processMap(map)
        }
    }

    private fun processList(list: SCList, builder: SCListBuilder = SCListBuilder()): SCListBuilder = builder.apply {
        for (item in list) add(builderOf(item))
    }

    private fun processMap(map: SCMap, builder: SCMapBuilder = SCMapBuilder()): SCMapBuilder = builder.apply {
        val merger = StructuredPropertiesMerger(policy, builder)
        for ((key, value) in map) value.accept(merger, key)
    }

    private fun builderOf(element: SCElement): SCElementBuilder = when (element) {
        is SCMap -> processMap(element)
        is SCList -> processList(element)
        is SCPrimitive -> primitive(element.content)
        else -> Null
    }
}

private class StructuredPropertiesTagger(
    val tags: Set<List<String>>,
    policy: StructuredPropertiesHolder.OverwritePolicy,
    builder: SCMapBuilder,
) : SCElement.Visitor<List<String>, Unit> {
    private val merger: StructuredPropertiesMerger = StructuredPropertiesMerger(policy, builder)

    override fun visitNull(ctx: List<String>) = place(ctx, SCNull)

    override fun visitPrimitive(prim: SCPrimitive, ctx: List<String>) = place(ctx, prim)

    override fun visitList(list: SCList, ctx: List<String>) = place(ctx, list)

    override fun visitMap(map: SCMap, ctx: List<String>) {
        if (map.isEmpty()) place(ctx, map)
        else for ((key, value) in map) value.accept(this, ctx + key)
    }

    private fun place(path: List<String>, value: SCElement) {
        if (path.isEmpty()) return
        for ((key, element) in nest(cut(path, tags), value))
            element.accept(merger, key)
    }
}

private operator fun <K, V> MutableMap<K, V>.set(key: K, overwrite: Boolean, value: V) {
    if (overwrite) put(key, value) else putIfAbsent(key, value)
}

private fun cut(path: List<String>, tags: Set<List<String>>): List<String> {
    if (tags.isEmpty()) return path
    val result = ArrayList(path)
    val used = HashSet<List<String>>()
    var index = 0
    while (index < result.size) {
        val match = tags.asSequence()
            .filter { it !in used && it.size < result.size && matchesAt(result, index, it) }
            .maxByOrNull { it.size }
        if (match == null) index++
        else {
            repeat(match.size) { result.removeAt(index) }
            used += match
        }
    }
    return result
}

private fun matchesAt(segments: List<String>, index: Int, tag: List<String>): Boolean =
    index + tag.size <= segments.size && tag.indices.all { segments[index + it] == tag[it] }

private fun nest(path: List<String>, value: SCElement): SCMap {
    var current: SCElement = value
    for (key in path.asReversed()) current = SCMap(mapOf(key to current))
    return current as SCMap
}
