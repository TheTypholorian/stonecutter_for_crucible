@file:OptIn(ExperimentalContracts::class, StonecutterInternalAPI::class)

package dev.kikugie.stonecutter.data.deserialization

import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlPath
import dev.eav.tomlkt.TomlElement
import dev.kikugie.commons.takeAs
import dev.kikugie.commons.takeAsOrNull
import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.util.escape
import dev.kikugie.stonecutter.util.toIntAutoRadix
import dev.kikugie.stonecutter.util.toIntOrNullAutoRadix
import dev.kikugie.stonecutter.util.toLongAutoRadix
import dev.kikugie.stonecutter.util.toLongOrNullAutoRadix
import dev.kikugie.stonecutter.util.toRadixString
import kotlinx.serialization.json.JsonElement
import java.util.Locale
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class SCStruct

@SCStruct
public sealed class SCElement {
    public abstract val content: Any?

    public abstract override fun toString(): String
    public abstract fun <C, T> accept(visitor: Visitor<C, T>, ctx: C): T

    public fun asLiteral(): SCLiteral {
        contract { returns() implies (this@SCElement is SCLiteral) }
        return takeAs()
    }

    public fun asLiteralOrNull(): SCLiteral? {
        contract { returnsNotNull() implies (this@SCElement is SCLiteral) }
        return takeAsOrNull()
    }

    public fun asList(): SCList {
        contract { returns() implies (this@SCElement is SCList) }
        return takeAs()
    }

    public fun asListOrNull(): SCList? {
        contract { returnsNotNull() implies (this@SCElement is SCList) }
        return takeAsOrNull()
    }

    public fun asMap(): SCMap {
        contract { returns() implies (this@SCElement is SCMap) }
        return takeAs()
    }

    public fun asMapOrNull(): SCMap? {
        contract { returnsNotNull() implies (this@SCElement is SCMap) }
        return takeAsOrNull()
    }

    public fun toJson(): JsonElement = accept(ToJsonVisitor, null)

    public fun toToml(): TomlElement = accept(ToTomlVisitor, null)

    public fun toYaml(): YamlNode = accept(ToYamlVisitor, YamlPath.root)

    public interface Visitor<C, T> {
        public fun visitNull(ctx: C): T
        public fun visitLiteral(literal: SCLiteral, ctx: C): T
        public fun visitList(list: SCList, ctx: C): T
        public fun visitMap(map: SCMap, ctx: C): T
    }
}

@SCStruct
public object SCNull : SCElement() {
    override val content: Nothing? get() = null

    override fun toString(): String = "null"
    override fun <C, T> accept(visitor: Visitor<C, T>, ctx: C): T = visitor.visitNull(ctx)
}

@SCStruct
public class SCLiteral @StonecutterInternalAPI constructor(override val content: String, public val type: Type) : SCElement() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SCLiteral) return false

        if (content != other.content) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = content.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }

    override fun toString(): String = content

    override fun <C, T> accept(visitor: Visitor<C, T>, ctx: C): T = visitor.visitLiteral(this, ctx)

    public fun toBoolean(): Boolean = content.lowercase(Locale.US).toBooleanStrict()

    public fun toBooleanOrNull(): Boolean? = content.lowercase(Locale.US).toBooleanStrictOrNull()

    public fun toInt(): Int = content.toIntAutoRadix()

    public fun toIntOrNull(): Int? = content.toIntOrNullAutoRadix()

    public fun toLong(): Long = content.toLongAutoRadix()

    public fun toLongOrNull(): Long? = content.toLongOrNullAutoRadix()

    public fun toFloat(): Float = when (content.lowercase(Locale.US)) {
        "inf", "+inf" -> Float.POSITIVE_INFINITY
        "-inf" -> Float.NEGATIVE_INFINITY
        "nan" -> Float.NaN
        else -> content.toFloat()
    }

    public fun toFloatOrNull(): Float? = when (content.lowercase(Locale.US)) {
        "inf", "+inf" -> Float.POSITIVE_INFINITY
        "-inf" -> Float.NEGATIVE_INFINITY
        "nan" -> Float.NaN
        else -> content.toFloatOrNull()
    }

    public fun toDouble(): Double = when (content.lowercase(Locale.US)) {
        "inf", "+inf" -> Double.POSITIVE_INFINITY
        "-inf" -> Double.NEGATIVE_INFINITY
        "nan" -> Double.NaN
        else -> content.toDouble()
    }

    public fun toDoubleOrNull(): Double? = when (content.lowercase(Locale.US)) {
        "inf", "+inf" -> Double.POSITIVE_INFINITY
        "-inf" -> Double.NEGATIVE_INFINITY
        "nan" -> Double.NaN
        else -> content.toDoubleOrNull()
    }

    public enum class Type {
        Boolean, Integer, Float, String
    }
}

@SCStruct
@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
public class SCList(override val content: List<SCElement>) : SCElement(), List<SCElement> by content {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SCList) return false

        if (content != other.content) return false

        return true
    }

    override fun hashCode(): Int {
        return content.hashCode()
    }

    override fun toString(): String = content.joinToString(prefix = "[", postfix = "]")

    override fun <C, T> accept(visitor: Visitor<C, T>, ctx: C): T = visitor.visitList(this, ctx)
}

@SCStruct
public class SCMap(override val content: Map<String, SCElement>) : SCElement(), Map<String, SCElement> by content {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SCMap) return false

        if (content != other.content) return false

        return true
    }

    override fun hashCode(): Int {
        return content.hashCode()
    }

    override fun toString(): String = content.entries.joinToString(prefix = "{", postfix = "}") { (k, v) -> "${k.escape()}: $v" }

    override fun <C, T> accept(visitor: Visitor<C, T>, ctx: C): T = visitor.visitMap(this, ctx)
}

public fun Boolean.toSCLiteral(): SCLiteral = SCLiteral(toString(), SCLiteral.Type.Boolean)
public fun String.toSCLiteral(): SCLiteral = SCLiteral(this, SCLiteral.Type.String)
public fun Float.toSCLiteral(): SCLiteral = SCLiteral(toString(), SCLiteral.Type.Float)
public fun Double.toSCLiteral(): SCLiteral = SCLiteral(toString(), SCLiteral.Type.Float)
public fun Int.toSCLiteral(radix: Int = 10): SCLiteral = SCLiteral(toRadixString(radix), SCLiteral.Type.Integer)
public fun Long.toSCLiteral(radix: Int = 10): SCLiteral = SCLiteral(toRadixString(radix), SCLiteral.Type.Integer)