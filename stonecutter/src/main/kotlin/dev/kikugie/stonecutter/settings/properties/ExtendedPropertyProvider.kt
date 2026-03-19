@file:OptIn(StonecutterExperimentalAPI::class)

package dev.kikugie.stonecutter.settings.properties

import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.semver.data.StringVersion
import dev.kikugie.semver.data.Version
import dev.kikugie.semver.impl.VersionParsingException
import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.deserialization.JsonConverter
import dev.kikugie.stonecutter.data.deserialization.determineConverter
import dev.kikugie.stonecutter.data.version.LenientOperations
import dev.kikugie.stonecutter.data.version.SemanticOperations
import dev.kikugie.stonecutter.settings.properties.ExtendedPropertyProvider.ExtendedPropertyConfig
import dev.kikugie.stonecutter.util.toRegularFile
import kotlinx.serialization.json.JsonElement
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtraPropertiesExtension
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale
import kotlin.reflect.KClass

public interface ExtendedPropertyProvider {
    /**
     * Configuration holder for extended Gradle properties.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/properties#centralized-properties">Centralized Properties Documentation</a>
     */
    public val properties: ExtendedPropertyConfig

    /** Configures the [properties] extension. */
    public infix fun properties(action: Action<ExtendedPropertyConfig>): Unit = action.execute(properties)

    /**
     * Loads additional properties from a [file].
     *
     * Unlike the `.properties` format, this extension supports nested keys,
     * which are resolved in the following ways relative to the project invoking this function:
     * - If a map key is a subproject name, its entries are only loaded
     * if this project is a child of the project that requested the file:
     * ```toml
     * [subproject]
     * my_property = "Hello World"
     * ```
     * ```kt
     * // settings.gradle.kts
     * sc.properties(file("properties.toml"), Toml2JsonConverter)
     * sc.create(rootProject) { versions("subproject") }
     *
     * assert(findProperty("my_property") == null)
     * assert(findProperty("subproject.my_property") != null)
     * ```
     * ```
     * // build.gradle.kts
     * assert(findProperty("my_property") != null)
     * ```
     * - Map keys registered with [sc.properties.tags()][ExtendedPropertyConfig.tags]
     * are skipped during the flattening:
     * ```toml
     * [my_tag]
     * my_property = "Hello World"
     * ```
     * ```kt
     * assert(findProperty("my_property") == null)
     * assert(findProperty("my_tag.my_property") != null)
     *
     * sc.properties.tags("my_tag")
     * assert(findProperty("my_property") != null)
     * ```
     * - Other keys are flattened:
     * ```toml
     * key1.value = "Hello"
     * key2 = {
     *   value = "World"
     * }
     * ```
     * ```kt
     * // build.gradle.kts
     * assert(findProperty("key1.value") != null)
     * assert(findProperty("key2.value") != null)
     * ```
     *
     * The default supported formats are `toml`, `json`, `json5`, `yml` and `yaml`,
     * which are automatically resolved based on the [file] extension.
     * Additional formats can be supported by providing a [JsonConverter] to the
     * overloaded function.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/properties#centralized-properties">Centralized Properties Documentation</a>
     */
    public infix fun properties(file: File): Unit = properties.load(file)

    /**
     * Loads additional properties from a [file] with the custom [converter].
     *
     * Unlike the `.properties` format, this extension supports nested keys,
     * which are resolved in the following ways relative to the project invoking this function:
     * - If a map key is a subproject name, its entries are only loaded
     * if this project is a child of the project that requested the file:
     * ```toml
     * [subproject]
     * my_property = "Hello World"
     * ```
     * ```kt
     * // settings.gradle.kts
     * sc.properties(file("properties.toml"), Toml2JsonConverter)
     * sc.create(rootProject) { versions("subproject") }
     *
     * assert(findProperty("my_property") == null)
     * assert(findProperty("subproject.my_property") != null)
     * ```
     * ```
     * // build.gradle.kts
     * assert(findProperty("my_property") != null)
     * ```
     * - Map keys registered with [sc.properties.tags()][ExtendedPropertyConfig.tags]
     * are skipped during the flattening:
     * ```toml
     * [my_tag]
     * my_property = "Hello World"
     * ```
     * ```kt
     * assert(findProperty("my_property") == null)
     * assert(findProperty("my_tag.my_property") != null)
     *
     * sc.properties.tags("my_tag")
     * assert(findProperty("my_property") != null)
     * ```
     * - Other keys are flattened:
     * ```toml
     * key1.value = "Hello"
     * key2 = {
     *   value = "World"
     * }
     * ```
     * ```kt
     * // build.gradle.kts
     * assert(findProperty("key1.value") != null)
     * assert(findProperty("key2.value") != null)
     * ```
     *
     * The default supported formats are `toml`, `json`, `json5`, `yml` and `yaml`,
     * which are automatically resolved when using the [properties] function without
     * the [converter] parameter.
     *
     * In other cases, a custom converter can be implemented as long as the underlying format
     * is compatible with Json types.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/properties#centralized-properties">Centralized Properties Documentation</a>
     */
    public fun properties(file: File, converter: JsonConverter): Unit = properties.load(file, converter)

    public abstract class ExtendedPropertyConfig {
        /**
         * Loads additional properties from a [file] with the custom [converter].
         *
         * Unlike the `.properties` format, this extension supports nested keys,
         * which are resolved in the following ways relative to the project invoking this function:
         * - If a map key is a subproject name, its entries are only loaded
         * if this project is a child of the project that requested the file:
         * ```toml
         * [subproject]
         * my_property = "Hello World"
         * ```
         * ```kt
         * // settings.gradle.kts
         * sc.properties(file("properties.toml"), Toml2JsonConverter)
         * sc.create(rootProject) { versions("subproject") }
         *
         * assert(findProperty("my_property") == null)
         * assert(findProperty("subproject.my_property") != null)
         * ```
         * ```
         * // build.gradle.kts
         * assert(findProperty("my_property") != null)
         * ```
         * - Map keys registered with [sc.properties.tags()][ExtendedPropertyConfig.tags]
         * are skipped during the flattening:
         * ```toml
         * [my_tag]
         * my_property = "Hello World"
         * ```
         * ```kt
         * assert(findProperty("my_property") == null)
         * assert(findProperty("my_tag.my_property") != null)
         *
         * sc.properties.tags("my_tag")
         * assert(findProperty("my_property") != null)
         * ```
         * - Other keys are flattened:
         * ```toml
         * key1.value = "Hello"
         * key2 = {
         *   value = "World"
         * }
         * ```
         * ```kt
         * // build.gradle.kts
         * assert(findProperty("key1.value") != null)
         * assert(findProperty("key2.value") != null)
         * ```
         *
         * The default supported formats are `toml`, `json`, `json5`, `yml` and `yaml`,
         * which are automatically resolved when using the [properties] function without
         * the [converter] parameter.
         *
         * In other cases, a custom converter can be implemented as long as the underlying format
         * is compatible with Json types.
         * @see <a href="https://stonecutter.kikugie.dev/wiki/config/properties#centralized-properties">Centralized Properties Documentation</a>
         */
        public abstract fun load(file: File, converter: JsonConverter = determineConverter(file))

        /**
         * Adds a list of tags to the config.
         *
         * Tags represent table keys, which should not be flattened as described in the [load] specification.
         *
         * Tags are applied both to already loaded and future property files.
         * @see <a href="https://stonecutter.kikugie.dev/wiki/config/properties#property-tags">Property Tags Documentation</a>
         */
        public abstract fun tags(vararg tags: String)

        /**
         * Retrieves the structured property as converted [JsonConverter]
         * at the path defined by [segments].
         *
         * Values are only queried from files registered with the [load] function.
         * Gradle's native properties are **not included**.
         *
         * When querying the element, the segments are evaluated as follows:
         * - Each segment must be map key or an array index:
         * ```toml
         * items = [true, "1"]
         * ```
         * ```kt
         * val first: JsonPrimitive = sc.properties.raw("items", "0").jsonPrimitive
         * assert(first.toString() == "true")
         *
         * val second: JsonPrimitive = properties.raw("items", "1").jsonPrimitive
         * assert(second.toString() == "\"1\"")
         * assert(second.removeSurrounding("\").toInt() == 1)
         * ```
         * - Project paths and tags are skipped:
         * ```toml
         * ["1.21.1"]
         * tag.item = "Hello!"
         * ```
         * ```kt
         * assert(sc.current.project == "1.21.1")
         *
         * sc.properties.tags("tag")
         *
         * val item: JsonPrimitive = sc.properties.raw("item").jsonPrimitive
         * assert(item.toString() == "\"Hello!\"")
         * ```
         * - The deepest match wins:
         * ```toml
         * item = "Hello!"
         *
         * ["1.21.1"]
         * item = [1, 2, 3]
         * ```
         * ```kt
         * assert(sc.current.project == "1.21.1")
         *
         * val item: JsonElement = sc.properties.raw("item")
         * assert(item is JsonArray)
         * ```
         *
         * Note that this function is intended to be used to pass serializable values
         * from properties to tasks. All returned elements can be converted to valid
         * Json with [JsonElement.toString].
         *
         * @param segments Path to the required element;
         * each segment represents a map key or an array index
         * @return [JsonElement] located at the path;
         * other formats are converted to Json during deserialization
         * @throws IllegalArgumentException If there's no element at the provided path
         */
        @StonecutterExperimentalAPI
        public fun raw(vararg segments: String): JsonElement = requireNotNull(rawOrNull(*segments)) {
            "No element at path '${segments.joinToString(":")}'"
        }

        /**
         * Retrieves the structured property as converted [JsonConverter]
         * at the path defined by [segments].
         *
         * Values are only queried from files registered with the [load] function.
         * Gradle's native properties are **not included**.
         *
         * Elements are queried the same way as in the non-nullable [raw] function.
         *
         * Note that this function is intended to be used to pass serializable values
         * from properties to tasks. All returned elements can be converted to valid
         * Json with [JsonElement.toString].
         *
         * @param segments Path to the required element;
         * each segment represents a map key or an array index
         * @return [JsonElement] located at the path or `null` if it's not found;
         * other formats are converted to Json during deserialization
         */
        @StonecutterExperimentalAPI
        public abstract fun rawOrNull(vararg segments: String): JsonElement?

        /**
         * Retrieves a property with the given [name], converted to the [T] type.
         *
         * Properties are queried from [ExtraPropertiesExtension], making use
         * of ones defined with Gradle's regular mechanisms as well.
         *
         * The conversion process first stringifies the property value,
         * and then parses it as the requested type.
         *
         * @return Property [name] converted to [T]
         * @param T Requested value type; may be one of:
         * - [Boolean]: case-insensitive `"true"` or `"false"`
         * - [Short], [Int], [Long], [Float], [Double]:
         *   parsed with `java.lang.<type>.parse<type>()`
         * - [Version], [StringVersion], [SemanticVersion]:
         *   parsed with the corresponding [Version.parse] override
         * @throws [IllegalArgumentException] If the property with this [name] is not defined
         * @throws [IllegalArgumentException] If [T] is not one of the listed supported types
         * @throws [IllegalArgumentException] If [T] is [Boolean] and the value can't be parsed as such
         * @throws [NumberFormatException] If [T] is a [Number] and the value can't be parsed as such
         * @throws [VersionParsingException] If [T] is a [Version] and the value can't be parsed as such
         */
        @StonecutterExperimentalAPI
        public inline operator fun <reified T : Any> get(name: String): T =
            getTypedImpl(name, T::class).getOrThrow()

        /**
         * Retrieves a property with the given [name], converted to the [T] type if it exists.
         *
         * Properties are queried from [ExtraPropertiesExtension], making use
         * of ones defined with Gradle's regular mechanisms as well.
         *
         * The conversion process first stringifies the property value,
         * and then parses it as the requested type.
         *
         * @return Property [name] converted to [T] or `null` if it's not defined.
         * @param T Requested value type; may be one of:
         * - [Boolean]: case-insensitive `"true"` or `"false"`
         * - [Short], [Int], [Long], [Float], [Double]:
         *   parsed with `java.lang.<type>.parse<type>()`
         * - [Version], [StringVersion], [SemanticVersion]:
         *   parsed with the corresponding [Version.parse] override
         * @throws [IllegalArgumentException] If [T] is not one of the listed supported types
         * @throws [IllegalArgumentException] If [T] is [Boolean] and the value can't be parsed as such
         * @throws [NumberFormatException] If [T] is a [Number] and the value can't be parsed as such
         * @throws [VersionParsingException] If [T] is a [Version] and the value can't be parsed as such
         */
        @StonecutterExperimentalAPI
        public inline fun <reified T : Any> getOrNull(name: String): T? =
            getTypedImpl(name, T::class).getOrNull()

        @PublishedApi
        internal abstract fun <T : Any> getTypedImpl(name: String, cls: KClass<T>): Result<T>
    }
}

internal class ExtendedPropertyConfigImpl(
    val hierarchy: ProjectHierarchy,
    val ext: ExtraPropertiesExtension,
    val props: ExtendedPropertiesStorage,
    val objects: ObjectFactory,
) : ExtendedPropertyConfig() {
    private val tags: MutableSet<String> = mutableSetOf()

    override fun rawOrNull(vararg segments: String): JsonElement? =
        props.get(hierarchy, tags, segments.toList())

    override fun <T : Any> getTypedImpl(name: String, cls: KClass<T>): Result<T> = kotlin
        .runCatching { requireNotNull(ext[name]) { "No such property '$name'" } }
        .map { it.toString().mapTo(cls) }

    override fun tags(vararg tags: String) {
        props.apply(hierarchy, ext, tags.toSet() - this.tags)
        this.tags += tags
    }

    override fun load(file: File, converter: JsonConverter) {
        val result = props.load(file.toRegularFile(objects), converter, hierarchy)
            ?: throw FileNotFoundException(file.path)
        result.apply(hierarchy, ext, tags)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> String.mapTo(cls: KClass<T>): T = when (cls.java) {
        String::class.java -> this
        Boolean::class.java -> lowercase(Locale.US).toBooleanStrict()
        Int::class.java -> toInt()
        Long::class.java -> toLong()
        Short::class.java -> toShort()
        Double::class.java -> toDouble()
        Float::class.java -> toFloat()
        Version::class.java -> LenientOperations.tryParse(this).getOrThrow()
        StringVersion::class.java -> StringVersion.parse(this)
        SemanticVersion::class.java -> SemanticOperations.tryParse(this).getOrThrow()
        else -> throw IllegalArgumentException("Unsupported type: ${cls.simpleName}")
    } as T
}
