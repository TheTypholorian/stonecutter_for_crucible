package dev.kikugie.stonecutter.settings.properties

import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.settings.properties.ExtendedPropertyProvider.ExtendedPropertyConfig
import dev.kikugie.stonecutter.util.toRegularFile
import org.gradle.api.Action
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtraPropertiesExtension
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject

@OptIn(StonecutterExperimentalAPI::class)
public interface ExtendedPropertyProvider {
    public val properties: ExtendedPropertyConfig
    public infix fun properties(action: Action<ExtendedPropertyConfig>): Unit = action.execute(properties)

    /**
     * Loads properties from a TOML [file].
     */
    @StonecutterExperimentalAPI
    public infix fun properties(file: File): Unit = properties.load(file)

    @StonecutterExperimentalAPI
    public interface ExtendedPropertyConfig {
        public fun load(file: File)
        public fun tags(vararg tags: String)
    }
}

@OptIn(StonecutterExperimentalAPI::class)
internal abstract class ExtendedPropertyConfigImpl @Inject constructor(
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
