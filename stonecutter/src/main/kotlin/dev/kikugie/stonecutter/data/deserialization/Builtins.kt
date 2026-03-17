@file:OptIn(StonecutterExperimentalAPI::class)

package dev.kikugie.stonecutter.data.deserialization

import com.charleskorn.kaml.*
import dev.eav.tomlkt.*
import dev.eav.tomlkt.TomlLiteral.Type
import dev.kikugie.commons.collections.present
import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.io.File
import java.util.*

internal val SUPPORTED_CONVERTIBLE: List<String> = listOf("json", "json5", "yml", "yaml", "toml")

internal inline fun <reified T> JsonConverter.deserialize(text: String): T =
    Json2JsonConverter.SPEC.decodeFromJsonElement(convert(text))

internal fun File.defaultConverterOrNull(): JsonConverter? = when (extension.lowercase(Locale.US)) {
    "json", "json5" -> Json2JsonConverter
    "yml", "yaml" -> Yaml2JsonConverter
    "toml" -> Toml2JsonConverter
    else -> null
}

internal fun determineConverter(
    file: File,
    docs: String = "https://stonecutter.kikugie.dev/wiki/config/projects"
): JsonConverter = requireNotNull(file.defaultConverterOrNull()) {
    """
        file://${file.absolutePath} is not of a supported format
        - Hint: use one of ${SUPPORTED_CONVERTIBLE.present()} formats or provide a custom converter
        - Docs: $docs
    """.trimIndent()
}

internal object Json2JsonConverter : JsonConverter {
    val SPEC = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        allowComments = true
        allowTrailingComma = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    override fun convert(text: String): JsonObject =
        SPEC.decodeFromString(text)
}

internal object Toml2JsonConverter : JsonConverter {
    val SPEC = Toml {
        ignoreUnknownKeys = true
    }

    override fun convert(text: String): JsonObject =
        SPEC.parseToTomlTable(text).convert() as JsonObject

    private fun visitTable(table: TomlTable): JsonElement = buildJsonObject {
        for ((k, v) in table) put(k, v.convert())
    }

    private fun visitArray(array: TomlArray): JsonElement = buildJsonArray {
        for (i in array) add(i.convert())
    }

    private fun visitLiteral(literal: TomlLiteral): JsonElement = when (literal.type) {
        Type.Boolean -> JsonPrimitive(literal.toBoolean())
        Type.Integer -> JsonPrimitive(literal.toLong())
        Type.Float -> JsonPrimitive(literal.toDouble())
        else -> JsonPrimitive(literal.content)
    }

    private fun TomlElement.convert(): JsonElement = when (this) {
        is TomlTable -> visitTable(this)
        is TomlArray -> visitArray(this)
        is TomlLiteral -> visitLiteral(this)
        TomlNull -> JsonNull
    }
}

internal object Yaml2JsonConverter : JsonConverter {
    val SPEC = Yaml(
        configuration = YamlConfiguration(
            strictMode = false
        )
    )

    override fun convert(text: String): JsonObject =
        SPEC.parseToYamlNode(text).convert() as JsonObject

    private fun visitMap(map: YamlMap): JsonElement = buildJsonObject {
        for ((k, v) in map.entries) put(k.content, v.convert())
    }

    private fun visitList(list: YamlList): JsonElement = buildJsonArray {
        for (i in list.items) add(i.convert())
    }

    private fun visitScalar(scalar: YamlScalar): JsonElement = when (val str = scalar.content) {
        ".inf", ".Inf", ".INF" -> JsonPrimitive(Double.POSITIVE_INFINITY)
        "-.inf", "-.Inf", "-.INF" -> JsonPrimitive(Double.NEGATIVE_INFINITY)
        ".nan", ".NaN", ".NAN" -> JsonPrimitive(Double.NaN)
        "true", "True", "TRUE" -> JsonPrimitive(true)
        "false", "False", "FALSE" -> JsonPrimitive(false)
        else -> tryConvertContent(str)
    }

    private fun tryConvertContent(str: String): JsonElement {
        // No matter the number format, it will have digits, so we can have a cheap check
        if (str.none(Char::isDigit)) return JsonPrimitive(str)
        return str.toDoubleOrNull()?.let(::JsonPrimitive)
            ?: str.toLongOrNull()?.let(::JsonPrimitive)
            ?: JsonPrimitive(str)
    }

    private fun YamlNode.convert(): JsonElement = when (this) {
        is YamlMap -> visitMap(this)
        is YamlList -> visitList(this)
        is YamlScalar -> visitScalar(this)
        is YamlNull -> JsonNull
        is YamlTaggedNode ->
            throw SerializationException("Deserialization of tagged YAML nodes is not supported (${path.toHumanReadableString()})")
    }
}
