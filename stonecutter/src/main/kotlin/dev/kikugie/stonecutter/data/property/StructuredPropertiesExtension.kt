package dev.kikugie.stonecutter.data.property

import dev.kikugie.stonecutter.data.deserialization.SCConverter
import dev.kikugie.stonecutter.data.deserialization.SCElement
import dev.kikugie.stonecutter.data.deserialization.determineConverter
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.File

public abstract class StructuredPropertiesExtension {
    public abstract fun load(file: RegularFile, converter: SCConverter)
    public fun load(file: RegularFile): Unit = load(file, determineConverter(file.asFile))

    public abstract fun load(file: File, converter: SCConverter)
    public fun load(file: File): Unit = load(file, determineConverter(file))

    public abstract fun tags(tags: Iterable<String>)
    public fun tags(vararg tags: String): Unit = tags(tags.asIterable())

    public abstract fun <T : Any> getAs(name: String, cls: Class<T>): T
    public inline fun <reified T : Any> getAs(name: String): T = getAs(name, T::class.java)

    public abstract fun getOrNull(name: String): String?
    public operator fun get(name: String): String = requireNotNull(getOrNull(name)) {
        "No such property '$name'"
    }

    public abstract fun getLazy(name: String): Provider<String>

    public abstract fun rawOrNull(name: String): SCElement?
    public fun raw(name: String): SCElement = requireNotNull(rawOrNull(name)) {
        "No such property '$name'"
    }

    public fun rawOrNull(vararg components: String): SCElement? =
        rawOrNull(components.joinToString("."))
    public fun raw(vararg components: String): SCElement =
        raw(components.joinToString("."))

    public operator fun contains(name: String): Boolean =
        getOrNull(name) != null
}
