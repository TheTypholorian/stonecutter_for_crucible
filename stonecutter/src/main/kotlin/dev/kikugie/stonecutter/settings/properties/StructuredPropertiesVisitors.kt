package dev.kikugie.stonecutter.settings.properties

import dev.kikugie.commons.text.getOrDefault
import dev.kikugie.stonecutter.data.deserialization.*
import dev.kikugie.stonecutter.settings.properties.PropertiesRetriever.Result
import dev.kikugie.stonecutter.util.join

internal abstract class PropertiesVisitor<T>(private val tags: Set<String>) : SCElement.Visitor<String, T> {
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

        return builder.toString()
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
    override fun visitNull(ctx: String) {
        // Don't add nulls
    }

    override fun visitPrimitive(prim: SCPrimitive, ctx: String) {
        consumer(cut(ctx).replace(':', '.'), prim.toString())
    }

    override fun visitList(list: SCList, ctx: String) {
        for ((i, e) in list.withIndex())
            e.accept(this, join(ctx, i.toString(), ":"))
    }

    override fun visitMap(map: SCMap, ctx: String) {
        for ((k, v) in map) join(ctx, k, ":").let {
            if (isValidPath(k)) v.accept(this, it)
            else PROPERTIES_LOGGER.warn("Invalid property path '$it'")
        }
    }
}

internal class PropertiesRetriever(tags: Set<String>, private val path: String) : PropertiesVisitor<Result>(tags) {
    class Result(val depth: Int, val element: SCElement? = null)

    override fun visitNull(ctx: String): Result =
        Result(ctx.depth, SCNull.takeIfValidPath(ctx))

    override fun visitPrimitive(prim: SCPrimitive, ctx: String): Result =
        Result(ctx.depth, prim.takeIfValidPath(ctx))

    override fun visitList(list: SCList, ctx: String): Result = findCandidate(
        list.withIndex(),
        Result(ctx.depth, list.takeIfValidPath(ctx))
    ) { (i, e) -> e.accept(this, join(ctx, i.toString(), ":")) }

    override fun visitMap(map: SCMap, ctx: String): Result = findCandidate(
        map.entries,
        Result(ctx.depth, map.takeIfValidPath(ctx))
    ) { (k, v) -> v.accept(this, join(ctx, k, ":")) }

    private val String.depth: Int get() = if (isEmpty()) 0 else count { it == ':' } + 1
    private fun <T> T.takeIfValidPath(it: String): T? {
        val short = cut(it)
        return takeIf { short == path }
    }
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

internal fun isValidPath(key: String): Boolean =
    !(key.isEmpty() || key.startsWith(':') || key.endsWith(':') || "::" in key)
