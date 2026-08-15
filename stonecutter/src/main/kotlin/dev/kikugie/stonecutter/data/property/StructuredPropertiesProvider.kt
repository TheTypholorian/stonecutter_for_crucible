package dev.kikugie.stonecutter.data.property

import dev.kikugie.stonecutter.data.deserialization.SCConverter
import org.gradle.api.Action
import java.io.File

public interface StructuredPropertiesProvider {
    public val properties: StructuredPropertiesExtension

    public infix fun properties(action: Action<in StructuredPropertiesExtension>): Unit =
        action.execute(properties)

    public infix fun properties(file: File): Unit =
        properties.load(file)

    public fun properties(file: File, converter: SCConverter): Unit =
        properties.load(file, converter)
}
