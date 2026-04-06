@file:OptIn(StonecutterExperimentalAPI::class)

package dev.kikugie.stonecutter.settings.properties

import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import dev.kikugie.stonecutter.data.deserialization.SCConverter
import dev.kikugie.stonecutter.data.deserialization.SCElement
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.namedDomainObjectList
import javax.inject.Inject

internal val PROPERTIES_LOGGER = Logging.getLogger("StructuredPropertiesLoader")
internal const val PROPERTIES_FILENAME = "stonecutter.properties.toml"

internal abstract class StructuredPropertiesLoader @Inject constructor(val objects: ObjectFactory, val providers: ProviderFactory) {
    private val container: NamedDomainObjectCollection<StructuredPropertiesEntry> = objects.namedDomainObjectList(StructuredPropertiesEntry::class)

    fun apply(extra: ExtraPropertiesExtension, tags: Set<String> = emptySet()) {
        val collector = PropertiesCollector(tags, extra::set)
        for (it in container) it.apply(collector)
    }

    fun get(path: String, tags: Set<String> = emptySet()): PropertiesRetriever.Result {
        val retriever = PropertiesRetriever(tags, path)
        return findCandidate(container) { it.get(retriever) }
    }

    fun load(file: RegularFile, converter: SCConverter): StructuredPropertiesEntry? = synchronized(container) {
        val path  = file.asFile.absolutePath
        PROPERTIES_LOGGER.debug("Loading properties from {}", path)
        container.findByName(path) ?: loadNew(file, converter)?.also(container::add)
    }

    private fun loadNew(file: RegularFile, converter: SCConverter): StructuredPropertiesEntry? {
        val contents = providers.fileContents(file).asText.orNull ?: return null
        return StructuredPropertiesEntry(converter.convert(contents), file)
    }
}

internal class StructuredPropertiesEntry(val entry: SCElement, file: RegularFile) : Named {
    private val path: String = file.asFile.absolutePath

    override fun getName(): String = path
    fun apply(collector: PropertiesCollector) = entry.accept(collector, "")
    fun get(retriever: PropertiesRetriever): PropertiesRetriever.Result = entry.accept(retriever, "")
}
