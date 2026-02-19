package dev.kikugie.stonecutter.settings.properties

import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.settings.properties.ExtendedPropertyProvider.ExtendedPropertyConfig
import dev.kikugie.stonecutter.util.toRegularFile
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtraPropertiesExtension
import java.io.File
import java.io.FileNotFoundException

@OptIn(StonecutterExperimentalAPI::class)
public interface ExtendedPropertyProvider {
    /**
     * Configuration holder for extended Gradle properties.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/properties#centralized-properties">Wiki link</a>
     */
    public val properties: ExtendedPropertyConfig

    /** Configures the [properties] extension. */
    public infix fun properties(action: Action<ExtendedPropertyConfig>): Unit = action.execute(properties)

    /**
     * Loads additional properties from a TOML [file].
     *
     * Unlike `.properties` format, TOML files support tables with nested values.
     * During evaluation, table keys are interpreted in one of the following ways:
     * - If a table key is a subproject name, its entries are only loaded if the subproject
     *   is a parent of the querying project.
     *   For example, `"1.21.1".my_property` is only added to `:1.21.1` project as `"my_property"`.
     * - If a table key is a tag added with [sc.properties.tags()][ExtendedPropertyConfig.tags],
     *   its children are added without the tag prefix.
     *   For example, `example_tag.my_property` with `tags("example_tag")` is added as `"my_property"`.
     * - Other table keys are flattened with dot separators.
     *   For example, `deps.kotlin = "..."` is added as `"deps.kotlin"`, as well as when declared as:
     *   ```toml
     *   [deps]
     *   kotlin = "..."
     *   ```
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/properties#centralized-properties">Wiki link</a>
     */
    @StonecutterExperimentalAPI
    public infix fun properties(file: File): Unit = properties.load(file)

    @StonecutterExperimentalAPI
    public interface ExtendedPropertyConfig {
        /**
         * Loads additional properties from a TOML [file].
         *
         * Unlike `.properties` format, TOML files support tables with nested values.
         * During evaluation, table keys are interpreted in one of the following ways:
         * - If a table key is a subproject name, its entries are only loaded if the subproject
         *   is a parent of the querying project.
         *   For example, `"1.21.1".my_property` is only added to `:1.21.1` project as `"my_property"`.
         * - If a table key is a tag added with [sc.properties.tags()][ExtendedPropertyConfig.tags],
         *   its children are added without the tag prefix.
         *   For example, `example_tag.my_property` with `tags("example_tag")` is added as `"my_property"`.
         * - Other table keys are flattened with dot separators.
         *   For example, `deps.kotlin = "..."` is added as `"deps.kotlin"`, as well as when declared as:
         *   ```toml
         *   [deps]
         *   kotlin = "..."
         *   ```
         * @see <a href="https://stonecutter.kikugie.dev/wiki/config/properties#centralized-properties">Wiki link</a>
         */
        public fun load(file: File)

        /**
         * Adds a list of tags to the config.
         *
         * Tags represent table keys, which should not be flattened as described in the [load] specification.
         *
         * Tags are applied both to already loaded and future property files.
         * @see <a href="https://stonecutter.kikugie.dev/wiki/config/properties#property-tags">Wiki link</a>
         */
        public fun tags(vararg tags: String)
    }
}

@OptIn(StonecutterExperimentalAPI::class)
internal class ExtendedPropertyConfigImpl(
    val hierarchy: ProjectHierarchy,
    val ext: ExtraPropertiesExtension,
    val props: ExtendedPropertiesStorage,
    val objects: ObjectFactory,
) : ExtendedPropertyConfig {
    private val tags: MutableSet<String> = mutableSetOf()

    override fun tags(vararg tags: String) {
        props.apply(hierarchy, ext, tags.toSet() - this.tags)
        this.tags += tags
    }

    override fun load(file: File) {
        val result = props.load(file.toRegularFile(objects), hierarchy)
            ?: throw FileNotFoundException(file.path)
        result.apply(hierarchy, ext, tags)
    }
}
