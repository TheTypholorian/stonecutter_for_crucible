package dev.kikugie.stonecutter.data.property

import dev.kikugie.commons.result.invalidArg
import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.semver.data.StringVersion
import dev.kikugie.semver.data.Version
import dev.kikugie.stonecutter.data.deserialization.*
import dev.kikugie.stonecutter.data.version.LenientOperations
import dev.kikugie.stonecutter.data.version.SemanticOperations
import dev.kikugie.stonecutter.util.toRegularFile
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import java.io.File
import java.util.*
import javax.inject.Inject

internal abstract class StructuredPropertiesImpl @Inject constructor(
    private val providers: ProviderFactory,
    private val objects: ObjectFactory,
) : StructuredPropertiesExtension() {
    private val types: TypeMapper = TypeMapper()
    val holder: StructuredPropertiesHolder = StructuredPropertiesHolder()

    init {
        types.register<CharSequence> { it }
        types.register<String> { it }
        types.register<Boolean> { it.lowercase(Locale.US).toBooleanStrict() }
        types.register<Byte> { it.toByte() }
        types.register<Short> { it.toShort() }
        types.register<Int> { it.toInt() }
        types.register<Long> { it.toLong() }
        types.register<Float> { it.toFloat() }
        types.register<Double> { it.toDouble() }
        types.register<UByte> { it.toUByte() }
        types.register<UShort> { it.toUShort() }
        types.register<UInt> { it.toUInt() }
        types.register<ULong> { it.toULong() }
        types.register<Version> { LenientOperations.tryParse(it).getOrThrow() }
        types.register<StringVersion> { StringVersion.parse(it) }
        types.register<SemanticVersion> { SemanticOperations.tryParse(it).getOrThrow() }
    }

    override fun load(file: RegularFile, converter: SCConverter) {
        holder.load(file.asFile) { readFile(file, converter) }
    }

    override fun load(file: File, converter: SCConverter) {
        holder.load(file) { readFile(file.toRegularFile(objects), converter) }
    }

    override fun tags(tags: Iterable<String>) {
        val filtered = tags.filter { it.isNotBlank() }
        for (tag in filtered) validateTag(tag)
        holder.tags(filtered)
    }

    override fun <T : Any> getAs(name: String, cls: Class<T>): T = try {
        types.get(getOrNull(name)!!, cls)
    } catch (_: NullPointerException) {
        invalidArg("No such property '$name'")
    } catch (e: RuntimeException) {
        throw IllegalArgumentException("Failed to convert '$name' to ${cls.simpleName}: ${e.message}").apply {
            initCause(e)
        }
    }

    override fun getLazy(name: String): Provider<String> =
        providers.provider { getOrNull(name) }

    override fun getOrNull(name: String): String? = when (val it = holder.get(name)) {
        is SCNull -> null
        is SCPrimitive -> it.toString()
        else -> providers.gradleProperty(name).orNull
    }

    override fun rawOrNull(path: String): SCElement? =
        holder.raw(path.replace(':', '.'))

    override fun contains(name: String, structOnly: Boolean): Boolean = when (val it = holder.get(name)) {
        is SCPrimitive -> true
        is SCNull -> false
        else -> !structOnly && providers.gradleProperty(name).isPresent
    }

    internal fun configureExport(enabled: Boolean, ext: ExtraPropertiesExtension): Unit =
        holder.configureExport(enabled, ext)

    internal fun configureWarnings(enabled: Boolean): Unit =
        holder.configureWarnings(enabled)

    internal fun loadDefaults(directory: Directory) {
        val file = DEFAULT_FILES
            .firstNotNullOfOrNull { name -> directory.file(name).takeIf { it.asFile.exists() } }
            ?: return
        holder.load(file.asFile, reload = false) { readFile(file, determineConverter(file.asFile)) }
    }

    private fun readFile(file: RegularFile, converter: SCConverter): SCMap? {
        val converted: SCElement = providers.fileContents(file).asText.orNull?.let(converter::convert)
            ?: return null
        require(converted is SCMap) { "Properties must be read as a map" }
        return converted
    }

    companion object {
        val DEFAULT_FILES: List<String> = listOf(
            "stonecutter.properties.toml",
            "stonecutter.properties.yaml",
            "stonecutter.properties.yml",
            "stonecutter.properties.json5",
            "stonecutter.properties.json"
        )
    }
}

@JvmInline
private value class TypeMapper(private val mappings: LinkedHashMap<Class<out Any>, (String) -> Any> = LinkedHashMap()) {
    @Suppress("UNCHECKED_CAST") fun <T : Any> get(value: String, cls: Class<out T>): T =
        requireNotNull(mappings[cls]) { "Value '$value' can't be converted to ${cls.simpleName}" }(value) as T

    fun <T : Any> register(cls: Class<T>, action: (String) -> T) {
        mappings[cls] = action
    }

    inline fun <reified T : Any> register(noinline action: (String) -> T) =
        register(T::class.java, action)
}

private fun validateTag(tag: String) {
    val segments: List<String> = tag.split(':')
    require(segments.none(String::isBlank)) {
        "Property tag '$tag' has an empty segment - ':' separates nested names and cannot be leading, trailing or repeated"
    }
    require(segments.none { segment -> segment.any(Char::isWhitespace) }) {
        "Property tag '$tag' contains whitespace"
    }
}
