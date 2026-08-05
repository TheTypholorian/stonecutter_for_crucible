package dev.kikugie.stonecutter.model.process

import dev.kikugie.stonecutter.model.Identifier
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*


@Serializable(with = ConfigurationRegistry.RegistrySerializer::class)
public class ConfigurationRegistry(private val registry: MutableMap<ConfigurationFlag<*>, Any> = mutableMapOf()) {
    @Suppress("UNCHECKED_CAST")
    public operator fun <T : Any> get(flag: ConfigurationFlag<T>): T =
        registry.getOrPut(flag) { flag.default } as T

    public operator fun <T : Any> set(flag: ConfigurationFlag<T>, value: T): Unit =
        registry.set(flag, value)

    public fun remove(flag: ConfigurationFlag<*>) {
        registry.remove(flag)
    }

    @Serializable
    private enum class ConfigurationType { BOOL, INT, FLOAT, STRING }

    @Serializable(with = ModelSerializer::class)
    private data class ConfigurationModel(val type: ConfigurationType, val default: Any, val value: Any = default)

    @OptIn(ExperimentalSerializationApi::class)
    private object ModelSerializer : KSerializer<ConfigurationModel> {
        private const val TYPE: Int = 0
        private const val DEFAULT: Int = 1
        private const val VALUE: Int = 2
        private val FQN: String = ConfigurationModel::class.qualifiedName!!

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor(FQN) {
            val any = ContextualSerializer(Any::class).descriptor

            element<ConfigurationType>("type")
            element("default", any)
            element("value", any, isOptional = true)
        }

        override fun serialize(encoder: Encoder, value: ConfigurationModel): Unit = encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, TYPE, ConfigurationType.serializer(), value.type)
            when (value.type) {
                ConfigurationType.BOOL -> encodeModelValues(value.default as Boolean, value.value as Boolean)
                ConfigurationType.INT -> encodeModelValues(value.default as Long, value.value as Long)
                ConfigurationType.FLOAT -> encodeModelValues(value.default as Double, value.value as Double)
                ConfigurationType.STRING -> encodeModelValues(value.default as String, value.value as String)
            }
        }

        override fun deserialize(decoder: Decoder): ConfigurationModel = decoder.decodeStructure(descriptor) {
            var type: ConfigurationType? = null
            var default: Any? = null
            var value: Any? = null

            while (true) when (val index = decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break
                TYPE -> type = decodeSerializableElement(descriptor, index, ConfigurationType.serializer())
                DEFAULT -> default = decodeModelValue(type, index)
                VALUE -> value = decodeModelValue(type, index)
                else -> error("Index $index is not in [-1; 2] in deserialization of $FQN")
            }

            checkNotNull(type) { "Type token has not been read for $FQN" }
            checkNotNull(default) { "Default value has not been read for $FQN" }
            ConfigurationModel(type, default, value ?: default)
        }

        private fun CompositeDecoder.decodeModelValue(type: ConfigurationType?, index: Int): Any = when (
            checkNotNull(type) {"Can't decode $FQN value before its type token"}
        ) {
            ConfigurationType.BOOL -> decodeBooleanElement(descriptor, index)
            ConfigurationType.INT -> decodeLongElement(descriptor, index)
            ConfigurationType.FLOAT -> decodeDoubleElement(descriptor, index)
            ConfigurationType.STRING -> decodeStringElement(descriptor, index)
        }

        private inline fun <reified T> CompositeEncoder.encodeModelValues(default: T, value: T) {
            val serializer = kotlinx.serialization.serializer<T>()
            encodeSerializableElement(descriptor, DEFAULT, serializer, default)
            if (default != value)
                encodeSerializableElement(descriptor, VALUE, serializer, value)
        }
    }

    public object RegistrySerializer : KSerializer<ConfigurationRegistry> {
        private val delegate: KSerializer<Map<Identifier, ConfigurationModel>> =
            MapSerializer(Identifier.serializer(), ConfigurationModel.serializer())

        override val descriptor: SerialDescriptor get() = delegate.descriptor

        override fun serialize(encoder: Encoder, value: ConfigurationRegistry) {
            val models = mutableMapOf<Identifier, ConfigurationModel>()
            for ((key, value) in value.registry)
                models[key.key] = ConfigurationModel(key.determineType(), key.default, value)
            delegate.serialize(encoder, models)
        }

        override fun deserialize(decoder: Decoder): ConfigurationRegistry {
            val models: Map<Identifier, ConfigurationModel> = delegate.deserialize(decoder)
            val registry: MutableMap<ConfigurationFlag<*>, Any> = mutableMapOf()
            for ((key, model) in models) {
                val flag = ConfigurationFlag(key, model.default)
                registry[flag] = model.value
            }

            return ConfigurationRegistry(registry)
        }

        private fun ConfigurationFlag<*>.determineType(): ConfigurationType = when (val it = default) {
            is Boolean -> ConfigurationType.BOOL
            is Long -> ConfigurationType.INT
            is Double -> ConfigurationType.FLOAT
            is String -> ConfigurationType.STRING
            else -> error("Invalid type ${it::class.qualifiedName}")
        }
    }
}
