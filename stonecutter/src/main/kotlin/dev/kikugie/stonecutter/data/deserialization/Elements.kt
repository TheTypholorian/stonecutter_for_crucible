@file:OptIn(ExperimentalContracts::class, StonecutterInternalAPI::class)

package dev.kikugie.stonecutter.data.deserialization

import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlPath
import dev.eav.tomlkt.TomlElement
import dev.kikugie.commons.takeAs
import dev.kikugie.commons.takeAsOrNull
import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.util.escape
import kotlinx.serialization.json.JsonElement
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class SCStruct

@SCStruct
public sealed class SCElement {
    public abstract val content: Any?

    public abstract override fun toString(): String
    public abstract fun <C, T> accept(visitor: Visitor<C, T>, ctx: C): T

    public fun asPrimitive(): SCPrimitive {
        contract { returns() implies (this@SCElement is SCPrimitive) }
        return takeAs()
    }

    public fun asPrimitiveOrNull(): SCPrimitive? {
        contract { returnsNotNull() implies (this@SCElement is SCPrimitive) }
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

    public fun toJson(): JsonElement = accept(JsonConverter, null)

    public fun toToml(): TomlElement = accept(TomlConverter, null)

    public fun toYaml(): YamlNode = accept(YamlConverter, YamlPath.root)

    public interface Visitor<C, T> {
        public fun visitNull(ctx: C): T
        public fun visitPrimitive(prim: SCPrimitive, ctx: C): T
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
public class SCPrimitive private constructor(override val content: Any, public val kind: Kind) : SCElement() {
    public constructor(value: Boolean) : this(value, Kind.Boolean)
    public constructor(value: Int) : this(value.toLong(), Kind.Integer)
    public constructor(value: Long) : this(value, Kind.Integer)
    public constructor(value: Float) : this(value.toDouble(), Kind.Float)
    public constructor(value: Double) : this(value, Kind.Float)
    public constructor(value: CharSequence) : this(value.toString(), Kind.String)
    public constructor(value: String) : this(value, Kind.String)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SCPrimitive) return false

        if (content != other.content) return false
        if (kind != other.kind) return false

        return true
    }

    override fun hashCode(): Int {
        var result = content.hashCode()
        result = 31 * result + kind.hashCode()
        return result
    }

    override fun toString(): String = content.toString()
    override fun <C, T> accept(visitor: Visitor<C, T>, ctx: C): T = visitor.visitPrimitive(this, ctx)

    public fun toBoolean(): Boolean = of<Boolean>(Kind.Boolean)
    public fun toBooleanOrNull(): Boolean? = content as? Boolean

    public fun toInt(): Int = of<Long>(Kind.Integer).toInt()
    public fun toIntOrNull(): Int? = content.takeAsOrNull<Long>()?.toInt()

    public fun toLong(): Long = of(Kind.Integer)
    public fun toLongOrNull(): Long? = content as? Long

    public fun toFloat(): Float = of<Double>(Kind.Float).toFloat()
    public fun toFloatOrNull(): Float? = content.takeAsOrNull<Double>()?.toFloat()

    public fun toDouble(): Double = of(Kind.Float)
    public fun toDoubleOrNull(): Double? = content as? Double

    private inline fun <reified T> of(kind: Kind): T {
        require(kind == this.kind) { "Value ${content::class.simpleName} is not of kind $kind" }
        return content as T
    }

    public enum class Kind {
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
