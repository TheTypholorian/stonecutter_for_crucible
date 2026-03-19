package dev.kikugie.stonecutter.data.deserialization

import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import kotlinx.serialization.json.JsonObject

/**
 * A functional interface for converting structured text data into a JSON representation.
 *
 * Converters are used in places where Stonecutter accepts serialized data,
 * such as project setup and custom properties.
 * The deserialization process goes through `text -> json -> type` as the
 * type deserializer may use the resolved JSON types for dynamic coersion.
 */
@StonecutterExperimentalAPI
public fun interface JsonConverter {
    /**
     * Parses the [text] to the native format and transforms it into a [JsonObject]
     */
    public fun convert(text: String): JsonObject
}