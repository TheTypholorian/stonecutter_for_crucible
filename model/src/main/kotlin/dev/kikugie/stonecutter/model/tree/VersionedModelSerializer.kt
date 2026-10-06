package dev.kikugie.stonecutter.model.tree

import dev.kikugie.stonecutter.model.SCHEMA_VERSION
import dev.kikugie.stonecutter.model.SCHEMA_FIELD
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject

private val SCHEMA_ENTRY: Map<String, JsonPrimitive> = mapOf(SCHEMA_FIELD to JsonPrimitive(SCHEMA_VERSION))

internal abstract class VersionedModelSerializer<T : Any>(serializer: KSerializer<T>) : JsonTransformingSerializer<T>(serializer) {
    override fun transformSerialize(element: JsonElement): JsonElement =
        JsonObject(SCHEMA_ENTRY + element.jsonObject)

    override fun transformDeserialize(element: JsonElement): JsonElement =
        JsonObject(element.jsonObject - SCHEMA_FIELD)
}
