package dev.kikugie.stonecutter.settings.properties

import dev.kikugie.commons.text.getOrDefault
import dev.kikugie.stonecutter.data.deserialization.JsonVisitor
import dev.kikugie.stonecutter.data.deserialization.accept
import dev.kikugie.stonecutter.settings.properties.PropertiesRetriever.Result
import dev.kikugie.stonecutter.util.join
import dev.kikugie.stonecutter.util.unwrap
import kotlinx.serialization.json.*

internal abstract class PropertiesVisitor<T>(private val tags: Set<String>) : JsonVisitor<String, T> {
    protected fun check(key: String): Boolean =
        !(key.isEmpty() || key.startsWith(':') || key.endsWith(':') || "::" in key)

    protected fun cut(path: String): String {
        var offset = 0
        val builder = StringBuilder(path)
        val used = mutableSetOf<String>()

        while (true) {
            var candidate = ""
            for (tag in tags) if (tag.passes(offset, builder, candidate, used))
                candidate = tag

            if (candidate.isNotEmpty())
                builder.cutSegment(offset, candidate, used)
            else {
                val next = builder.indexOf(':', offset)
                if (next < 0) break else offset = next + 1
            }
        }

        return builder.toString().replace(':', '.')
    }

    private fun StringBuilder.cutSegment(offset: Int, candidate: String, used: MutableSet<String>) {
        if (getOrDefault(offset - 1) == ':') replace(offset - 1, offset + candidate.length, "")
        else if (getOrDefault(offset + candidate.length) == ':') replace(offset, offset + candidate.length + 1, "")
        else replace(offset, candidate.length, "")
        used += candidate
    }

    private fun String.passes(offset: Int, path: CharSequence, cand: String, used: Set<String>): Boolean = when {
        length < cand.length -> false // Shorter than the last best
        length >= path.length -> false // Out of bounds
        this in used -> false // Can't use more than once
        path.getOrDefault(offset + length, ':') != ':' -> false // Doesn't stop at :
        else -> path.startsWith(this, offset) // Doesn't match the segment
    }
}

internal class PropertiesCollector(tags: Set<String>, private val consumer: (String, String) -> Unit) : PropertiesVisitor<Unit>(tags) {
    override fun visitObject(obj: JsonObject, data: String) {
        for ((k, v) in obj) join(data, k, ":").let {
            if (check(k)) v.accept(this, it)
            else PROPERTIES_LOGGER.warn("Invalid property path '$it'")
        }
    }

    override fun visitArray(arr: JsonArray, data: String) {
        for ((i, e) in arr.withIndex())
            e.accept(this, join(data, i.toString(), ":"))
    }

    override fun visitPrimitive(prim: JsonPrimitive, data: String) {
        consumer(cut(data), prim.unwrap())
    }

    override fun visitNull(data: String) {
        // Don't add nulls
    }
}

internal class PropertiesRetriever(tags: Set<String>, private val path: String) : PropertiesVisitor<Result>(tags) {
    class Result(val depth: Int, val element: JsonElement? = null)

    override fun visitObject(obj: JsonObject, data: String): Result = findCandidate(
        obj.entries,
        Result(data.depth, obj.takeIfValidPath(data))
    ) { (k, v) -> v.accept(this, join(data, k, ":")) }

    override fun visitArray(arr: JsonArray, data: String): Result = findCandidate(
        arr.withIndex(),
        Result(data.depth, arr.takeIfValidPath(data))
    ) { (i, e) -> e.accept(this, join(data, i.toString(), ":")) }

    override fun visitPrimitive(prim: JsonPrimitive, data: String): Result =
        Result(data.depth, prim.takeIfValidPath(data))

    override fun visitNull(data: String): Result =
        Result(data.depth, JsonNull.takeIfValidPath(data))

    private val String.depth: Int get() = if (isEmpty()) 0 else count { it == ':' } + 1
    private fun <T> T.takeIfValidPath(p: String): T? = takeIf { p == path }
}

internal inline fun <T> findCandidate(iter: Iterable<T>, initial: Result = Result(0), mapping: (T) -> Result?): Result {
    var candidate = initial
    for (element in iter) {
        val result = mapping(element) ?: continue
        if (candidate.element == null && result.element == null) candidate = result
        else if (result.element != null && result.depth >= candidate.depth) candidate = result
    }
    return candidate
}
