package dev.kikugie.stonecutter.data.property

import dev.kikugie.commons.takeAsOrNull
import dev.kikugie.stonecutter.data.deserialization.*
import dev.kikugie.stonecutter.data.property.StructuredPropertiesHolder.OverwritePolicy.*
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

private val LOGGER: Logger = LoggerFactory.getLogger("StonecutterProperties")

// If you can, don't look here
internal class StructuredPropertiesHolder {
    private val appliedTags: HashSet<String> = HashSet()
    private val loadedFiles: HashSet<File> = HashSet()
    private val loadedStructs: ArrayList<SCMap> = ArrayList()
    private val primitiveCache: HashMap<String, SCElement?> = HashMap()
    private val rawCache: HashMap<String, SCElement?> = HashMap()

    private var overwritePolicy: OverwritePolicy = MERGE
    private var flattenedMap: SCMap = SCMap(emptyMap())
    private var lookupIndex: Map<String, List<FlatEntry>> = emptyMap()
    private var parsedTags: Array<Array<String>> = emptyArray()
    internal var extraProperties: ExtraPropertiesExtension? = null
    private var exposeProperties: Boolean = false
    private var warnOnDuplicate: Boolean = false
    private val exportedKeys: HashSet<String> = HashSet()

    fun all(): SCMap = flattenedMap[""]?.takeAsOrNull<SCMap>()
        ?: SCMap(emptyMap())

    fun get(path: String): SCElement? {
        if (path in primitiveCache) return primitiveCache[path]
        val best = resolve(path)
        val primitive = if (best is SCMap || best is SCList) null else best
        return primitive.also { primitiveCache[path] = it }
    }

    fun raw(path: String): SCElement? {
        if (path in rawCache) return rawCache[path]
        val best = resolve(path)
        val result = if (best is SCMap || best is SCList) mergeMatches(path, best) else best
        return result.also { rawCache[path] = it }
    }

    private fun resolve(path: String): SCElement? {
        if (path.isEmpty()) return flattenedMap[""]

        var best: FlatEntry? = null
        for (bucket in candidateKeys(path)) for (entry in lookupIndex[bucket].orEmpty()) {
            val current = best
            if (current != null && !entry.isDeeperThan(current)) continue
            if (reduces(entry.segments, path, parsedTags)) best = entry
        }
        return best?.value
    }

    private fun mergeMatches(path: String, best: SCElement): SCElement {
        val matches = ArrayList<FlatEntry>()
        for (bucket in candidateKeys(path)) for (entry in lookupIndex[bucket].orEmpty())
            if (sameKind(entry.value, best) && reduces(entry.segments, path, parsedTags)) matches += entry
        if (matches.size <= 1) return best

        // Apply shallowest-first so the deepest, last match wins any conflict.
        matches.sortWith(compareBy({ it.segments.size }, { it.order }))
        return if (best is SCList) SCList(matches.flatMap { (it.value as SCList).content })
        else mergeMaps(matches.map { it.value as SCMap })
    }

    private fun mergeMaps(maps: List<SCMap>): SCMap = with(SCMapBuilder()) {
        val merger = StructuredPropertiesMerger(overwritePolicy, this)
        for (map in maps) for ((key, value) in map) value.accept(merger, key)
        build()
    }

    private fun candidateKeys(path: String): Set<String> {
        val keys = hashSetOf(path)
        var dot = path.indexOf('.')
        while (dot >= 0) {
            keys += path.substring(dot + 1)
            dot = path.indexOf('.', dot + 1)
        }
        for (tag in parsedTags)
            keys += tag.last()
        return keys
    }

    private fun buildIndex(entries: Map<String, SCElement>): Map<String, List<FlatEntry>> {
        val index = HashMap<String, MutableList<FlatEntry>>()
        for ((order, entry) in entries.entries.withIndex()) {
            val segments = segmentsOf(entry.key)
            val last = segments.lastOrNull() ?: continue
            index.getOrPut(last) { ArrayList() }.add(FlatEntry(segments, entry.value, order))
        }
        return index
    }

    fun tags(tags: Iterable<String>): Boolean {
        val remainder: Set<String> = (tags.toSet() - appliedTags).ifEmpty { return false }
        appliedTags += remainder

        val offset = parsedTags.size
        val array = arrayOfNulls<Array<String>>(offset + remainder.size)
        parsedTags.copyInto(array)
        for ((index, tag) in remainder.withIndex()) {
            val segments: Array<String> = tag.split(':').toTypedArray()
            array[index + offset] = segments
        }
        primitiveCache.clear()
        rawCache.clear()
        @Suppress("UNCHECKED_CAST")
        parsedTags = array as Array<Array<String>>
        refreshExport()
        return true
    }

    fun reset() {
        parsedTags = emptyArray()
        primitiveCache.clear()
        appliedTags.clear()
        rawCache.clear()
        refreshExport()
    }

    fun load(file: File?, reload: Boolean = true, data: () -> SCMap?): Boolean {
        if (file != null && file in loadedFiles) return false

        loadedStructs += data() ?: return false
        if (file != null) loadedFiles += file

        if (reload) reload()
        return true
    }

    fun overwrite(policy: OverwritePolicy) {
        if (policy == overwritePolicy) return

        overwritePolicy = policy
        reload()
    }

    internal fun reload(): Unit = with(SCMapBuilder()) {
        val merger = StructuredPropertiesMerger(overwritePolicy, this)
        for (struct in loadedStructs) for ((key, value) in struct)
            value.accept(merger, key)

        val flattener = StructuredPropertiesFlattener(LinkedHashMap(), warnOnDuplicate)
        build().accept(flattener, "")

        primitiveCache.clear()
        rawCache.clear()
        flattenedMap = SCMap(flattener.builder)
        lookupIndex = buildIndex(flattener.builder)
        refreshExport()
    }

    internal fun configureExport(enabled: Boolean, ext: ExtraPropertiesExtension?) {
        exposeProperties = enabled
        extraProperties = ext
        refreshExport()
    }

    internal fun configureWarnings(enabled: Boolean) {
        if (enabled == warnOnDuplicate) return

        warnOnDuplicate = enabled
        reload()
    }

    private fun refreshExport() {
        val ext = extraProperties ?: return
        for (key in exportedKeys) ext[key] = null
        exportedKeys.clear()
        if (!exposeProperties) return
        for ((key, value) in exportedProperties()) {
            ext[key] = value
            exportedKeys += key
        }
    }

    private fun exportedProperties(): Map<String, String> {
        val keys = HashSet<String>()
        for ((key, value) in flattenedMap) {
            if (value !is SCPrimitive) continue
            keys += reduceKey(segmentsOf(key))
        }
        val result = LinkedHashMap<String, String>()
        for (key in keys) {
            val value = get(key)
            if (value is SCPrimitive) result[key] = value.toString()
        }
        return result
    }

    private fun reduceKey(segments: List<String>): String {
        if (parsedTags.isEmpty()) return segments.joinToString(".")
        val result = ArrayList<String>(segments.size)
        val used = BooleanArray(parsedTags.size)
        var index = 0
        while (index < segments.size) {
            var skipped = false
            for ((i, tag) in parsedTags.withIndex()) {
                if (!used[i] && matchesAt(segments, index, tag)) {
                    used[i] = true
                    index += tag.size
                    skipped = true
                    break
                }
            }
            if (!skipped) result += segments[index++]
        }
        return result.joinToString(".")
    }

    enum class OverwritePolicy {
        KEEP, MERGE, WRITE,
    }
}

private class StructuredPropertiesMerger(val policy: StructuredPropertiesHolder.OverwritePolicy, val builder: SCMapBuilder) :
    SCElement.Visitor<String, Unit> {
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

private class StructuredPropertiesFlattener(val builder: MutableMap<String, SCElement>, val warnOnDuplicate: Boolean) :
    SCElement.Visitor<String, Unit> {
    override fun visitNull(ctx: String): Unit = append(ctx, SCNull)
    override fun visitPrimitive(prim: SCPrimitive, ctx: String): Unit = append(ctx, prim)
    override fun visitList(list: SCList, ctx: String) {
        append(ctx, list)
        for ((index, item) in list.withIndex())
            item.accept(this, if (ctx.isEmpty()) index.toString() else "$ctx:$index")
    }

    override fun visitMap(map: SCMap, ctx: String) {
        append(ctx, map)
        for ((key, value) in map)
            value.accept(this, if (ctx.isEmpty()) key else "$ctx:$key")
    }

    private fun append(key: String, value: SCElement) {
        val previous = builder.put(key, value) ?: return
        val message = "Duplicate property replaced '$key': $previous"
        if (warnOnDuplicate) LOGGER.warn(message) else LOGGER.debug(message)
    }
}

private operator fun <K, V> MutableMap<K, V>.set(key: K, overwrite: Boolean, value: V) {
    if (overwrite) put(key, value) else putIfAbsent(key, value)
}

private fun segmentsOf(key: String): List<String> =
    if (key.isEmpty()) emptyList() else key.split(':')

private fun sameKind(a: SCElement, b: SCElement): Boolean =
    (a is SCMap && b is SCMap) || (a is SCList && b is SCList)

private fun matchSegment(target: String, offset: Int, s: String): Int = when {
    offset == 0 -> if (target.startsWith(s)) s.length else -1
    offset < target.length && target[offset] == '.' && target.startsWith(s, offset + 1) -> offset + 1 + s.length
    else -> -1
}

private fun matchesAt(segments: List<String>, index: Int, tag: Array<String>): Boolean =
    index + tag.size <= segments.size && tag.indices.all { segments[index + it] == tag[it] }

// Magic wikipedia algorithm pt.2
private fun reduces(segments: List<String>, target: String, tags: Array<Array<String>>): Boolean {
    if (target.isEmpty()) return segments.isEmpty()

    val length = segments.size
    val visited = HashSet<Triple<Int, Int, Int>>()
    val queue = ArrayDeque<Triple<Int, Int, Int>>()

    fun enqueue(index: Int, offset: Int, mask: Int) {
        val state = Triple(index, offset, mask)
        if (visited.add(state)) queue.add(state)
    }

    enqueue(0, 0, 0)
    while (queue.isNotEmpty()) {
        val (index, offset, mask) = queue.removeFirst()
        if (index == length) {
            if (offset == target.length) return true
            continue
        }

        // Keep segments[index], matching it against the next chunk of the target.
        val kept = matchSegment(target, offset, segments[index])
        if (kept >= 0) enqueue(index + 1, kept, mask)

        // Skip an as-yet-unused tag that matches contiguously at index.
        for ((i, tag) in tags.withIndex()) {
            val bit = 1 shl i
            if (mask and bit == 0 && matchesAt(segments, index, tag))
                enqueue(index + tag.size, offset, mask or bit)
        }
    }
    return false
}

private class FlatEntry(val segments: List<String>, val value: SCElement, val order: Int) {
    fun isDeeperThan(other: FlatEntry): Boolean = segments.size > other.segments.size
        || (segments.size == other.segments.size && order > other.order)
}
