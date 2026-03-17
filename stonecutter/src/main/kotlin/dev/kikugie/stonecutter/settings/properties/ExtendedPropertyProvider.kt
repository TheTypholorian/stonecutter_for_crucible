@file:OptIn(StonecutterExperimentalAPI::class)

package dev.kikugie.stonecutter.settings.properties

import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.deserialization.JsonConverter
import dev.kikugie.stonecutter.data.deserialization.determineConverter
import dev.kikugie.stonecutter.settings.properties.ExtendedPropertyProvider.ExtendedPropertyConfig
import dev.kikugie.stonecutter.util.toRegularFile
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtraPropertiesExtension
import java.io.File
import java.io.FileNotFoundException

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
    }
}

internal class ExtendedPropertyConfigImpl(
    val hierarchy: ProjectHierarchy,
    val ext: ExtraPropertiesExtension,
    val props: ExtendedPropertiesStorage,
    val objects: ObjectFactory,
) : ExtendedPropertyConfig() {
    private val tags: MutableSet<String> = mutableSetOf()

    override fun tags(vararg tags: String) {
        props.apply(hierarchy, ext, tags.toSet() - this.tags)
        this.tags += tags
    }

    override fun load(file: File, converter: JsonConverter) {
        val result = props.load(file.toRegularFile(objects), converter, hierarchy)
            ?: throw FileNotFoundException(file.path)
        result.apply(hierarchy, ext, tags)
    }
}
