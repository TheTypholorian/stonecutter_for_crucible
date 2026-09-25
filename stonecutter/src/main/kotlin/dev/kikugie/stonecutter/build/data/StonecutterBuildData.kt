package dev.kikugie.stonecutter.build.data

import dev.kikugie.stonecutter.AnyVersion
import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.controller.flag.StonecutterFlag
import dev.kikugie.stonecutter.controller.flag.StonecutterFlags
import dev.kikugie.stonecutter.model.process.FilesModel
import dev.kikugie.stonecutter.model.process.ReplacementModel
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import javax.inject.Inject

internal abstract class StonecutterBuildData @Inject constructor(
    flags: StonecutterFlags,
    current: Identifier,
    providers: ProviderFactory
) {
    @get:Input abstract val constants: MapProperty<Identifier, Boolean>
    @get:Input abstract val swaps: MapProperty<Identifier, String>
    @get:Input abstract val dependencies: MapProperty<Identifier, AnyVersion>
    @get:Input abstract val replacements: ListProperty<ReplacementModel>
    @get:Input abstract val files: Property<FilesModel>

    @get:Internal internal abstract val dummyDependencies: MapProperty<Identifier, AnyVersion>

    @get:Inject protected abstract val objects: ObjectFactory

    init {
        constants.set(mutableMapOf())
        swaps.set(mutableMapOf())
        replacements.set(mutableListOf())
        files.convention(FilesModel())
        dependencies.set(providers.provider { patchImplicitDependency(dummyDependencies, flags[StonecutterFlag.IMPLICIT_RECEIVER], current) })

        dummyDependencies.set(mutableMapOf())
    }
}

private fun patchImplicitDependency(
    prop: MapProperty<Identifier, AnyVersion>,
    key: Identifier,
    version: AnyVersion
): Map<Identifier, AnyVersion> = buildMap {
    putAll(prop.get())
    val implicit = getOrDefault(key, version)
    this[""] = implicit
    this[key] = implicit
}