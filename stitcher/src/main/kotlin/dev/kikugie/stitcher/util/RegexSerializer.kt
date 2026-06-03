@file:OptIn(ExperimentalSerializationApi::class)

package dev.kikugie.stitcher.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.pcre4j.regex.Pattern

private const val PATTERN: Int = 0
private const val FLAGS: Int = 1

object RegexSerializer : KSerializer<Regex> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("kotlin.text.Regex") {
        element<String>("pattern")
        element<Int>("flags", isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: Regex): Unit = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, PATTERN, value.pattern)

        val flags = value.toPattern().flags()
        if (flags != 0) encodeIntElement(descriptor, FLAGS, flags)
    }

    override fun deserialize(decoder: Decoder): Regex = decoder.decodeStructure(descriptor) {
        var flags = 0
        var pattern: String? = null
        while (true) when (val index = decodeElementIndex(descriptor)) {
            DECODE_DONE -> break
            PATTERN -> pattern = decodeStringElement(descriptor, PATTERN)
            FLAGS -> flags = decodeIntElement(descriptor, FLAGS)
            else -> throw SerializationException("Unexpected index $index")
        }

        if (pattern == null) throw MissingFieldException("pattern", descriptor.serialName)
        Regex(pattern, flags)
    }
}

object PerlSerializer : KSerializer<Pattern> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("org.pcre4j.regex.Pattern") {
        element<String>("pattern")
        element<Int>("flags", isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: Pattern): Unit = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, PATTERN, value.pattern())
        if (value.flags() != 0) encodeIntElement(descriptor, FLAGS, value.flags())
    }

    override fun deserialize(decoder: Decoder): Pattern = decoder.decodeStructure(descriptor) {
        var flags = 0
        var pattern: String? = null
        while (true) when (val index = decodeElementIndex(descriptor)) {
            DECODE_DONE -> break
            PATTERN -> pattern = decodeStringElement(descriptor, PATTERN)
            FLAGS -> flags = decodeIntElement(descriptor, FLAGS)
            else -> throw SerializationException("Unexpected index $index")
        }

        if (pattern == null) throw MissingFieldException("pattern", descriptor.serialName)
        Pattern.compile(pattern, flags)
    }
}

