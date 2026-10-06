package dev.kikugie.stonecutter.model

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer

private typealias SerializationExceptionHandler = (SerializationException) -> Unit

private val errorHandler: ThreadLocal<SerializationExceptionHandler?> = ThreadLocal()

public inline fun <reified T> Json.decodeModel(string: String, noinline onError: SerializationExceptionHandler): T =
    decodeModel(serializersModule.serializer<T>(), string, onError)

public fun <T> Json.decodeModel(deserializer: DeserializationStrategy<T>, string: String, onError: SerializationExceptionHandler): T {
    val previous = errorHandler.get()
    errorHandler.set(onError)
    return try {
        decodeFromString(deserializer, string)
    } finally {
        errorHandler.set(previous)
    }
}

internal fun reportSkipped(entry: String, cause: Exception) {
    errorHandler.get()?.invoke(SerializationException("Skipped unreadable $entry", cause))
}

internal open class LenientListSerializer<T>(private val element: KSerializer<T>, private val kind: String) : KSerializer<List<T>> {
    private val delegate: KSerializer<List<T>> = ListSerializer(element)

    override val descriptor: SerialDescriptor get() = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<T>): Unit =
        delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<T> {
        if (decoder !is JsonDecoder) return delegate.deserialize(decoder)
        return decoder.decodeJsonElement().jsonArray.mapNotNull {
            decoder.json.decodeLeniently(element, it) { "$kind $it" }
        }
    }
}

internal open class LenientMapSerializer<V>(private val value: KSerializer<V>, private val kind: String) : KSerializer<Map<String, V>> {
    private val delegate: KSerializer<Map<String, V>> = MapSerializer(String.serializer(), value)

    override val descriptor: SerialDescriptor get() = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, V>): Unit =
        delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): Map<String, V> {
        if (decoder !is JsonDecoder) return delegate.deserialize(decoder)
        val result = LinkedHashMap<String, V>()
        for ((key, entry) in decoder.decodeJsonElement().jsonObject)
            decoder.json.decodeLeniently(value, entry) { "$kind '$key': $entry" }?.let { result[key] = it }
        return result
    }
}

private inline fun <T> Json.decodeLeniently(serializer: KSerializer<T>, element: JsonElement, entry: () -> String): T? = try {
    decodeFromJsonElement(serializer, element)
} catch (e: IllegalArgumentException) {
    reportSkipped(entry(), e)
    null
} catch (e: IllegalStateException) {
    reportSkipped(entry(), e)
    null
}
