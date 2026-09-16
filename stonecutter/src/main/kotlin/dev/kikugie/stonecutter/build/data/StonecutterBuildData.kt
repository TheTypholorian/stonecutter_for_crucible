package dev.kikugie.stonecutter.build.data

import dev.kikugie.stitcher.document.issue.ProblemReport
import dev.kikugie.stonecutter.AnyVersion
import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.controller.flag.StonecutterFlag
import dev.kikugie.stonecutter.controller.flag.StonecutterFlags
import dev.kikugie.stonecutter.model.process.RegexReplacement
import dev.kikugie.stonecutter.model.process.Replacement
import dev.kikugie.stonecutter.model.process.ReplacementFilter
import dev.kikugie.stonecutter.model.process.StringReplacement
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import javax.inject.Inject

internal abstract class StonecutterBuildData @Inject constructor(
    flags: StonecutterFlags,
    current: Identifier,
    providers: ProviderFactory
) {
    @get:Input abstract val constants: MapProperty<Identifier, Boolean>
    @get:Input abstract val swaps: MapProperty<Identifier, String>
    @get:Input abstract val dependencies: MapProperty<Identifier, AnyVersion>
    @get:Input abstract val replacements: ListProperty<Replacement>
    @get:Input abstract val replacementFilters: MapProperty<Int, ReplacementFilter>

    @get:Internal internal abstract val dummyDependencies: MapProperty<Identifier, AnyVersion>

    @get:Inject protected abstract val objects: ObjectFactory

    init {
        constants.set(mutableMapOf())
        swaps.set(mutableMapOf())
        replacements.set(mutableListOf())
        replacementFilters.set(mutableMapOf())
        dependencies.set(providers.provider { patchImplicitDependency(dummyDependencies, flags[StonecutterFlag.IMPLICIT_RECEIVER], current) })

        dummyDependencies.set(mutableMapOf())
    }

    interface StringReplacementSpec {
        @get:Input val target: Property<String>
        @get:Input val source: Property<String>
        @get:Input @get:Optional val identifier: Property<Identifier>
        @get:Input @get:Optional val enabled: Property<Boolean>

        fun build(): StringReplacement =
            StringReplacement(target.get(), source.get(), identifier.orNull, enabled.getOrElse(false))
    }

    interface RegexReplacementSpec {
        @get:Input val target: Property<String>
        @get:Input val pattern: Property<String>
        @get:Input @get:Optional val options: Property<Int>
        @get:Input @get:Optional val identifier: Property<Identifier>
        @get:Input @get:Optional val enabled: Property<Boolean>

        fun build(): RegexReplacement =
            RegexReplacement(target.get(), pattern.get(), options.getOrElse(0), identifier.orNull, enabled.getOrElse(false))
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

private fun ProblemReport.thr(): Nothing = throw GradleException("$label:\n$info")