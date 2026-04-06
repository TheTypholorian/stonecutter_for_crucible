@file:OptIn(StonecutterInternalAPI::class)

package dev.kikugie.stonecutter.data.deserialization

import com.charleskorn.kaml.*
import dev.eav.tomlkt.*
import dev.kikugie.commons.collections.present
import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.util.SCJSON
import dev.kikugie.stonecutter.util.unescape
import kotlinx.serialization.json.*
import java.io.File
import java.util.*

internal val SUPPORTED_CONVERTIBLE: List<String> = listOf("json", "json5", "yml", "yaml", "toml")

internal inline fun <reified T> SCConverter.deserialize(text: String): T =
    FromJsonConverter.SPEC.decodeFromJsonElement(convert(text).toJson())

internal fun File.defaultConverterOrNull(): SCConverter? = when (extension.lowercase(Locale.US)) {
    "json", "json5" -> FromJsonConverter
    "yml", "yaml" -> FromYamlConverter
    "toml" -> FromTomlConverter
    else -> null
}

internal fun determineConverter(
    file: File,
    docs: String = "https://stonecutter.kikugie.dev/wiki/config/projects"
): SCConverter = requireNotNull(file.defaultConverterOrNull()) {
    """
    file://${file.absolutePath} is not of a supported format
    - Hint: use one of ${SUPPORTED_CONVERTIBLE.present()} formats or provide a custom converter
    - Docs: $docs
    """.trimIndent()
}


@OptIn(StonecutterInternalAPI::class)
public fun interface SCConverter {
    public fun convert(text: String): SCElement
}

public object ToJsonVisitor : SCElement.Visitor<Nothing?, JsonElement> {
    override fun visitNull(ctx: Nothing?): JsonNull = JsonNull

    override fun visitLiteral(literal: SCLiteral, ctx: Nothing?): JsonPrimitive = when (literal.type) {
        SCLiteral.Type.String -> JsonPrimitive(literal.content)
        SCLiteral.Type.Float -> JsonPrimitive(literal.content.toDouble())
        SCLiteral.Type.Integer -> JsonPrimitive(literal.content.toLong())
        else -> JsonUnquotedLiteral(literal.content)
    }

    override fun visitList(list: SCList, ctx: Nothing?): JsonArray =
        JsonArray(list.map { it.accept(this, null) })

    override fun visitMap(map: SCMap, ctx: Nothing?): JsonObject =
        JsonObject(map.mapValues { (_, it) -> it.accept(this, null) })
}

public object ToTomlVisitor : SCElement.Visitor<Nothing?, TomlElement> {
    override fun visitNull(ctx: Nothing?): TomlNull = TomlNull

    override fun visitLiteral(literal: SCLiteral, ctx: Nothing?): TomlLiteral = when(literal.type) {
        SCLiteral.Type.Boolean -> TomlLiteral(literal.toBoolean())
        SCLiteral.Type.Integer -> TomlLiteral(literal.toLong())
        SCLiteral.Type.Float -> TomlLiteral(literal.toDouble())
        SCLiteral.Type.String -> TomlLiteral(literal.content)
    }

    override fun visitList(list: SCList, ctx: Nothing?): TomlArray =
        dev.eav.tomlkt.TomlArray(list.map { it.accept(this, null) })

    override fun visitMap(map: SCMap, ctx: Nothing?): TomlTable =
        TomlTable(map.mapValues { (_, it) -> it.accept(this, null) })
}

public object ToYamlVisitor : SCElement.Visitor<YamlPath, YamlNode> {
    private val NOWHERE: Location = Location(1, 1)

    override fun visitNull(ctx: YamlPath): YamlNull = YamlNull(ctx)

    override fun visitLiteral(literal: SCLiteral, ctx: YamlPath): YamlScalar = YamlScalar(literal.content, ctx)

    override fun visitList(list: SCList, ctx: YamlPath): YamlList {
        val items = list.mapIndexed { i, e ->
            e.accept(this, ctx.withListEntry(i, NOWHERE))
        }
        return YamlList(items, ctx)
    }

    override fun visitMap(map: SCMap, ctx: YamlPath): YamlMap {
        val entries = map.map { (k, v) ->
            val key = YamlScalar(k, ctx.withMapElementKey(k, NOWHERE))
            val value = v.accept(this, key.path.withMapElementValue(NOWHERE))
            key to value
        }
        return YamlMap(entries.toMap(), ctx)
    }
}

@StonecutterInternalAPI
public object FromJsonConverter : SCConverter {
    @JvmField public val SPEC: Json = SCJSON

    override fun convert(text: String): SCElement {
        val obj: JsonElement = SPEC.decodeFromString(text)
        return visitElement(obj)
    }

    private fun visitElement(element: JsonElement): SCElement = when (element) {
        JsonNull -> SCNull
        is JsonPrimitive -> element.content.toSCLiteral(element.isString)
        is JsonArray -> SCList(element.map(::visitElement))
        is JsonObject -> SCMap(element.mapValues { (_, it) -> visitElement(it) })
    }

    private fun String.toSCLiteral(quoted: Boolean): SCLiteral = when {
        quoted -> removeSurrounding("\"").unescape().toSCLiteral()
        this == "true" || this =="false" -> SCLiteral(this, SCLiteral.Type.Boolean)
        '.' in this || 'e' in this || 'E' in this -> SCLiteral(this, SCLiteral.Type.Float)
        else -> SCLiteral(this, SCLiteral.Type.Integer)
    }
}

@StonecutterInternalAPI
public object FromTomlConverter : SCConverter {
    @JvmField public val SPEC: Toml = Toml { ignoreUnknownKeys = true }

    override fun convert(text: String): SCElement {
        val table: TomlElement = SPEC.decodeFromString(text)
        return visitElement(table)
    }

    private fun visitElement(element: TomlElement): SCElement = when (element) {
        TomlNull -> SCNull
        is TomlLiteral -> when (element.type) {
            TomlLiteral.Type.Boolean -> SCLiteral(element.content, SCLiteral.Type.Boolean)
            TomlLiteral.Type.Integer -> SCLiteral(element.content, SCLiteral.Type.Integer)
            TomlLiteral.Type.Float -> SCLiteral(element.content, SCLiteral.Type.Float)
            else -> element.content.toSCLiteral()
        }
        is TomlArray -> SCList(element.map(::visitElement))
        is TomlTable -> SCMap(element.mapValues { (_, it) -> visitElement(it) })
    }
}

@StonecutterInternalAPI
public object FromYamlConverter : SCConverter {
    @JvmField public val SPEC: Yaml = Yaml(configuration = YamlConfiguration(strictMode = false))

    override fun convert(text: String): SCElement {
        val node: YamlNode = SPEC.parseToYamlNode(text)
        return visitNode(node)
    }

    private fun visitNode(node: YamlNode): SCElement = when (node) {
        is YamlNull -> SCNull
        is YamlScalar -> node.toSCLiteral()
        is YamlList -> SCList(node.items.map(::visitNode))
        is YamlMap -> SCMap(node.entries.map { (k, it) -> k.content to visitNode(it) }.toMap())
        is YamlTaggedNode -> visitNode(node.innerNode)
    }

    private fun YamlScalar.toSCLiteral(): SCLiteral = when(val str = content) {
        ".inf", ".Inf", ".INF" -> Double.POSITIVE_INFINITY.toSCLiteral()
        "-.inf", "-.Inf", "-.INF" -> Double.NEGATIVE_INFINITY.toSCLiteral()
        ".nan", ".NaN", ".NAN" -> Double.NaN.toSCLiteral()
        "true", "True", "TRUE" -> true.toSCLiteral()
        "false", "False", "FALSE" -> false.toSCLiteral()
        else if (str.none(Char::isDigit)) -> str.toSCLiteral()
        else -> str.toLongOrNull()?.toSCLiteral()
            ?: str.toDoubleOrNull()?.toSCLiteral()
            ?: str.toSCLiteral()
    }
}