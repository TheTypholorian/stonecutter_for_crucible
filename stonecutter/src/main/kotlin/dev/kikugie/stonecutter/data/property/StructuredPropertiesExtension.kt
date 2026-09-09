package dev.kikugie.stonecutter.data.property

import dev.kikugie.stonecutter.data.deserialization.SCConverter
import dev.kikugie.stonecutter.data.deserialization.SCElement
import dev.kikugie.stonecutter.data.deserialization.determineConverter
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Extension for loading and accessing structured properties.
 * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/structured-properties">Structured Properties Docs</a>
 */
public abstract class StructuredPropertiesExtension {
    /**
     * Loads structured properties from the provided [file] using the [converter].
     *
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/structured-properties">Structured Properties Docs</a>
     */
    public abstract fun load(file: RegularFile, converter: SCConverter)

    /**
     * Loads structured properties from the provided [file] using the
     * converter determined by the file format (Json(5), Toml, or Yaml).
     *
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/structured-properties">Structured Properties Docs</a>
     */
    public fun load(file: RegularFile): Unit = load(file, determineConverter(file.asFile))

    /**
     * Loads structured properties from the provided [file] using the [converter].
     *
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/structured-properties">Structured Properties Docs</a>
     */
    public abstract fun load(file: File, converter: SCConverter)

    /**
     * Loads structured properties from the provided [file] using the
     * converter determined by the file format (Json(5), Toml, or Yaml).
     *
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/structured-properties">Structured Properties Docs</a>
     */
    public fun load(file: File): Unit = load(file, determineConverter(file))

    /**
     * Adds a list of [tags] to the config.
     *
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/structured-properties#property-tags">Structured Property Tags</a>
     */
    public abstract fun tags(tags: Iterable<String>)

    /**
     * Adds a list of [tags] to the config.
     *
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/structured-properties#property-tags">Structured Property Tags</a>
     */
    public fun tags(vararg tags: String): Unit = tags(tags.asIterable())

    /**
     * Retrieves the property with the given [name], parsing it as the requested type [T].
     *
     * Properties are queried from loaded files, with a fallback to
     * [properties.gradleProperty][org.gradle.api.provider.ProviderFactory.gradleProperty].
     * Keys of nested property values are joined with the dot (`.`) character.
     *
     * Only [SCPrimitive][dev.kikugie.stonecutter.data.deserialization.SCPrimitive] values
     * may be returned - composite types are ignored and treated as if the [name] is invalid.
     * To get those, use [raw]/[rawOrNull] functions.
     *
     * @throws IllegalArgumentException If a property with this [name] is not defined
     * @throws IllegalArgumentException If the property value can't be converted to type [T]
     */
    public abstract fun <T : Any> getAs(name: String, cls: Class<T>): T

    /**
     * Retrieves the property with the given [name], parsing it as the requested type [T].
     *
     * Properties are queried from loaded files, with a fallback to
     * [properties.gradleProperty][org.gradle.api.provider.ProviderFactory.gradleProperty].
     * Keys of nested property values are joined with the dot (`.`) character.
     *
     * Only [SCPrimitive][dev.kikugie.stonecutter.data.deserialization.SCPrimitive] values
     * may be returned - composite types are ignored and treated as if the [name] is invalid.
     * To get those, use [raw]/[rawOrNull] functions.
     *
     * @throws IllegalArgumentException If a property with this [name] is not defined
     * @throws IllegalArgumentException If the property value can't be converted to type [T]
     */
    public inline fun <reified T : Any> getAs(name: String): T = getAs(name, T::class.java)

    /**
     * Retrieves the property with the given [name], or `null` if it's not found or defined as
     * [SCNull][dev.kikugie.stonecutter.data.deserialization.SCNull].
     *
     * Properties are queried from loaded files, with a fallback to
     * [properties.gradleProperty][org.gradle.api.provider.ProviderFactory.gradleProperty].
     * Keys of nested property values are joined with the dot (`.`) character.
     *
     * Only [SCPrimitive][dev.kikugie.stonecutter.data.deserialization.SCPrimitive] values
     * may be returned - composite types are ignored and treated as if the [name] is invalid.
     * To get those, use [raw]/[rawOrNull] functions.
     */
    public abstract fun getOrNull(name: String): String?

    /**
     * Retrieves the property with the given [name], or throws if it's not found or defined as
     * [SCNull][dev.kikugie.stonecutter.data.deserialization.SCNull].
     *
     * Properties are queried from loaded files, with a fallback to
     * [properties.gradleProperty][org.gradle.api.provider.ProviderFactory.gradleProperty].
     * Keys of nested property values are joined with the dot (`.`) character.
     *
     * Only [SCPrimitive][dev.kikugie.stonecutter.data.deserialization.SCPrimitive] values
     * may be returned - composite types are ignored and treated as if the [name] is invalid.
     * To get those, use [raw]/[rawOrNull] functions.
     *
     * @throws IllegalArgumentException If a property with this [name] is not defined
     */
    public operator fun get(name: String): String = requireNotNull(getOrNull(name)) {
        "No such property '$name'"
    }

    /**
     * Retrieves the property with the given [name] as a lazily evaluated [Provider].
     *
     * Properties are queried from loaded files, with a fallback to
     * [properties.gradleProperty][org.gradle.api.provider.ProviderFactory.gradleProperty].
     * Keys of nested property values are joined with the dot (`.`) character.
     *
     * Only [SCPrimitive][dev.kikugie.stonecutter.data.deserialization.SCPrimitive] values
     * may be returned - composite types are ignored and treated as if the [name] is invalid.
     * To get those, use [raw]/[rawOrNull] functions.
     */
    public abstract fun getLazy(name: String): Provider<String>

    /**
     * Retrieves the first [SCElement] with the given [path], or `null` if it's not found.
     *
     * The [path] may be dot (`.`) or colon (`:`) separated.
     * The former is recommended, as the latter is only kept for backwards-compatibility.
     *
     * The [path] is shortened with tags as in [get]/[getOrNull] functions,
     * but naturally composite elements can be returned here.
     */
    public abstract fun rawOrNull(path: String): SCElement?

    /**
     * Retrieves the first [SCElement] with the given [path], or throws if it's not found.
     *
     * The [path] may be dot (`.`) or colon (`:`) separated.
     * The former is recommended, as the latter is only kept for backwards-compatibility.
     *
     * The [path] is shortened with tags as in [get]/[getOrNull] functions,
     * but naturally composite elements can be returned here.
     *
     * @throws IllegalArgumentException If a property with this [path] is not defined
     */
    public fun raw(path: String): SCElement = requireNotNull(rawOrNull(path)) {
        "No such property '$path'"
    }

    /**
     * Retrieves the first [SCElement] with the given [components], or `null` if it's not found.
     *
     * The [components] may be dot (`.`) or colon (`:`) separated.
     * The former is recommended, as the latter is only kept for backwards-compatibility.
     *
     * The [components] are shortened with tags as in [get]/[getOrNull] functions,
     * but naturally composite elements can be returned here.
     */
    public fun rawOrNull(vararg components: String): SCElement? =
        rawOrNull(components.joinToString("."))

    /**
     * Retrieves the first [SCElement] with the given [components], or throws if it's not found.
     *
     * The [components] may be dot (`.`) or colon (`:`) separated.
     * The former is recommended, as the latter is only kept for backwards-compatibility.
     *
     * The [components] are shortened with tags as in [get]/[getOrNull] functions,
     * but naturally composite elements can be returned here.
     *
     * @throws IllegalArgumentException If a property with these [components] is not defined
     */
    public fun raw(vararg components: String): SCElement =
        raw(components.joinToString("."))

    /**
     * Returns `true` if a property with the given [name] exists.
     * If [structOnly] is `true`, only keys from structured properties are checked.
     * Otherwise, this includes properties provided by [properties.gradleProperty][org.gradle.api.provider.ProviderFactory.gradleProperty].
     */
    public abstract fun contains(name: String, structOnly: Boolean): Boolean

    /**
     * Returns `true` if a property with the given [name] exists.
     * This includes properties provided by [properties.gradleProperty][org.gradle.api.provider.ProviderFactory.gradleProperty].
     */
    public operator fun contains(name: String): Boolean =
        contains(name, false)
}
