package dev.kikugie.stonecutter.data.property

import dev.kikugie.stonecutter.data.deserialization.SCConverter
import org.gradle.api.Action
import java.io.File

/**
 * Provides access to the [StructuredPropertiesExtension] and shorthands for loading files into it.
 * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/structured-properties">Structured Properties Docs</a>
 */
public interface StructuredPropertiesProvider {
    /**
     * Structured properties extension accessor.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/structured-properties">Structured Properties Docs</a>
     */
    public val properties: StructuredPropertiesExtension

    /**
     * Configures the structured properties extension.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/structured-properties">Structured Properties Docs</a>
     */
    public infix fun properties(action: Action<in StructuredPropertiesExtension>): Unit =
        action.execute(properties)

    /**
     * Loads structured properties from the provided [file] using the
     * converter determined by the file format (Json(5), Toml, or Yaml).
     *
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/structured-properties">Structured Properties Docs</a>
     */
    public infix fun properties(file: File): Unit =
        properties.load(file)

    /**
     * Loads structured properties from the provided [file] using the [converter].
     *
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/structured-properties">Structured Properties Docs</a>
     */
    public fun properties(file: File, converter: SCConverter): Unit =
        properties.load(file, converter)
}
