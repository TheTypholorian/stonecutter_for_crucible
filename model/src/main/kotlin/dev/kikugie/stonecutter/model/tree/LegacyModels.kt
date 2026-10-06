package dev.kikugie.stonecutter.model.tree

import dev.kikugie.stonecutter.model.process.PerlReplacement
import dev.kikugie.stonecutter.model.process.RegexReplacement
import dev.kikugie.stonecutter.model.process.StringReplacement
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

internal fun coerceLegacyTree(tree: JsonObject): JsonObject = rebuildJsonObject(tree, "stonecutter", "vcs", "current", "branches", "nodes") {
    tree["flags"]?.let { put("flags", legacyFlags(it.jsonObject)) }
}

internal fun coerceLegacyNode(node: JsonObject): JsonObject = rebuildJsonObject(node, "project", "version", "active", "branch", "root", "flags") {
    node["parameters"]?.let { put("parameters", legacyParameters(it.jsonObject)) }
}

private fun legacyFlags(flags: JsonObject): JsonObject = when {
    flags.values.any { it is JsonObject } -> flags
    else -> JsonObject(flags.mapValues { (_, it) -> legacyFlag(it) })
}

private fun legacyFlag(element: JsonElement): JsonObject {
    val value: String = element.jsonPrimitive.content
    val (type: String, typed: JsonPrimitive) = value.toBooleanStrictOrNull()?.let { "BOOL" to JsonPrimitive(it) }
        ?: value.toLongOrNull()?.let { "INT" to JsonPrimitive(it) }
        ?: value.toDoubleOrNull()?.let { "FLOAT" to JsonPrimitive(it) }
        ?: ("STRING" to JsonPrimitive(value))
    return buildJsonObject {
        put("type", type)
        put("default", typed)
    }
}

private fun legacyParameters(parameters: JsonObject): JsonObject = rebuildJsonObject(parameters, "constants", "swaps", "files") {
    (parameters["targets"] ?: parameters["dependencies"])?.let { put("targets", it) }
    parameters["replacements"]?.let { replacements ->
        putJsonArray("replacements") { for (it in replacements.jsonArray) legacyReplacement(it.jsonObject).forEach(::add) }
    }
}

private fun legacyReplacement(entry: JsonObject): List<JsonObject> {
    if ("replacement" in entry) return listOf(entry)

    val kind: String = entry.getValue("type").jsonPrimitive.content.substringAfterLast('.')
    val identifier: String? = entry["identifier"]?.jsonPrimitive?.contentOrNull
    val enabled: Boolean = legacyEnabled(entry, identifier)
    val replacement: JsonObject = buildReplacementEntry(kind, entry, identifier)

    return legacyPatterns(entry).map { pattern ->
        buildReplacementModel(replacement, pattern, enabled)
    }
}

private fun buildReplacementModel(replacement: JsonObject, pattern: JsonElement, enabled: Boolean): JsonObject = buildJsonObject {
    put("replacement", JsonObject(replacement + ("pattern" to pattern)))
    put("enabled", enabled)
}

private fun buildReplacementEntry(kind: String, entry: JsonObject, identifier: String?): JsonObject = buildJsonObject {
    put("type", legacyReplacementType(kind))
    put("target", entry.getValue("target"))
    legacyPatternFlags(entry, kind).takeIf { it != 0 }?.let { put("flags", it) }
    if (identifier != null) put("identifier", identifier)
}

private fun legacyReplacementType(kind: String): String = when (kind) {
    "StringReplacement" -> StringReplacement.serializer().descriptor.serialName
    "RegexReplacement" -> RegexReplacement.serializer().descriptor.serialName
    "PerlReplacement" -> PerlReplacement.serializer().descriptor.serialName
    else -> throw SerializationException("Unknown legacy replacement type '$kind'")
}

private fun legacyPatterns(entry: JsonObject): List<JsonElement> =
    entry["sources"]?.jsonArray ?: listOf(entry["pattern"] ?: entry.getValue("regex").jsonObject.getValue("pattern"))

private fun legacyPatternFlags(entry: JsonObject, kind: String): Int = when (kind) {
    "RegexReplacement" -> legacyRegexFlags(entry["flags"] ?: entry["options"] ?: entry["regex"]?.jsonObject?.get("flags"))
    "PerlReplacement" -> ((entry["flags"] ?: entry["options"]) as? JsonPrimitive)?.intOrNull ?: 0
    else -> 0
}

private fun legacyEnabled(entry: JsonObject, identifier: String?): Boolean = identifier == null
    || listOf("enabled", "defaultEnabled", "default_enabled").any { entry[it]?.jsonPrimitive?.booleanOrNull == true }
    || entry["tags"]?.jsonArray?.any { it.jsonPrimitive.content == "DEFAULT_ENABLED" } == true

private fun legacyRegexFlags(element: JsonElement?): Int = when (element) {
    null -> 0
    is JsonArray -> Regex("", element.mapTo(mutableSetOf()) { RegexOption.valueOf(it.jsonPrimitive.content) }).toPattern().flags()
    else -> element.jsonPrimitive.intOrNull ?: 0
}

private fun JsonObjectBuilder.copy(source: JsonObject, vararg keys: String) {
    for (key in keys) source[key]?.let { put(key, it) }
}

private inline fun rebuildJsonObject(source: JsonObject, vararg keys: String, action: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
    copy(source, *keys)
    action()
}
