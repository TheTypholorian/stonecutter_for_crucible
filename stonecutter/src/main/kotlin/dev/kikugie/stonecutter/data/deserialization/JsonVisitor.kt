@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stonecutter.data.deserialization

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal interface JsonVisitor<D, T> {
    fun visitObject(obj: JsonObject, data: D): T
    fun visitArray(arr: JsonArray, data: D): T
    fun visitPrimitive(prim: JsonPrimitive, data: D): T
    fun visitNull(data: D): T
}

internal inline fun <D, T> JsonObject.accept(visitor: JsonVisitor<D, T>, data: D): T = visitor.visitObject(this, data)
internal inline fun <D, T> JsonArray.accept(visitor: JsonVisitor<D, T>, data: D): T = visitor.visitArray(this, data)
internal inline fun <D, T> JsonPrimitive.accept(visitor: JsonVisitor<D, T>, data: D): T = visitor.visitPrimitive(this, data)
internal inline fun <D, T> JsonNull.accept(visitor: JsonVisitor<D, T>, data: D): T = visitor.visitNull(data)

internal fun <D, T> JsonElement.accept(visitor: JsonVisitor<D, T>, data: D): T = when (this) {
    is JsonArray -> visitor.visitArray(this, data)
    is JsonObject -> visitor.visitObject(this, data)
    is JsonPrimitive -> visitor.visitPrimitive(this, data)
    JsonNull -> visitor.visitNull(data)
}
