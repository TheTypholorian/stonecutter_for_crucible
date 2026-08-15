@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stonecutter.data.deserialization

import dev.kikugie.stonecutter.data.deserialization.SCPrimitive.Kind

internal sealed interface SCElementBuilder {
    fun build(): SCElement
}

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class SCElementBuilderDsl

@JvmInline @SCElementBuilderDsl
internal value class SCPrimitiveBuilder(val value: Any) : SCElementBuilder {
    override fun build(): SCPrimitive = when (value) {
        is CharSequence -> SCPrimitive(value.toString(), Kind.String)
        is Boolean -> SCPrimitive(value, Kind.Boolean)
        is Int, is Long -> SCPrimitive(value.toLong(), Kind.Integer)
        is Float, is Double -> SCPrimitive(value.toDouble(), Kind.Float)
        else -> error("Invalid value type ${value::class.qualifiedName}")
    }
}

internal object SCNullBuilder : SCElementBuilder {
    override fun build(): SCNull = SCNull
}

@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
@JvmInline @SCElementBuilderDsl
internal value class SCListBuilder(private val items: MutableList<SCElementBuilder> = mutableListOf())
    : SCElementBuilder, MutableList<SCElementBuilder> by items {
    override fun build(): SCList = SCList(items.map(SCElementBuilder::build))
}

@JvmInline @SCElementBuilderDsl
internal value class SCMapBuilder(private val items: MutableMap<String, SCElementBuilder> = LinkedHashMap())
    : SCElementBuilder, MutableMap<String, SCElementBuilder> by items {
    override fun build(): SCMap = SCMap(items.mapValues { (_, it) -> it.build() })
}

internal inline val Null: SCNullBuilder
    get() = SCNullBuilder

internal inline fun primitive(value: Any): SCPrimitiveBuilder =
    SCPrimitiveBuilder(value)

internal inline fun list(block: SCListBuilder.() -> Unit): SCListBuilder =
    SCListBuilder().apply(block)

internal inline fun map(block: SCMapBuilder.() -> Unit): SCMapBuilder =
    SCMapBuilder().apply(block)
