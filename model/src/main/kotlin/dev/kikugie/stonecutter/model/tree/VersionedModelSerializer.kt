package dev.kikugie.stonecutter.model.tree

import dev.kikugie.stonecutter.model.SCHEMA_FIELD
import dev.kikugie.stonecutter.model.SCHEMA_VERSION
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

private val SCHEMA_ENTRY: Map<String, JsonPrimitive> = mapOf(SCHEMA_FIELD to JsonPrimitive(SCHEMA_VERSION))

internal abstract class VersionedModelSerializer<T : Any>(serializer: KSerializer<T>) : JsonTransformingSerializer<T>(serializer) {
    protected open fun coerce(element: JsonObject): JsonObject = element

    override fun transformSerialize(element: JsonElement): JsonElement =
        JsonObject(SCHEMA_ENTRY + element.jsonObject)

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val model = element.jsonObject
        return when (val schema = model[SCHEMA_FIELD]?.jsonPrimitive?.int) {
            SCHEMA_VERSION -> JsonObject(model - SCHEMA_FIELD)
            1, null -> coerce(JsonObject(model - SCHEMA_FIELD))
            else -> throw SerializationException("Unsupported model schema $schema, expected 1..$SCHEMA_VERSION")
        }
    }
}
