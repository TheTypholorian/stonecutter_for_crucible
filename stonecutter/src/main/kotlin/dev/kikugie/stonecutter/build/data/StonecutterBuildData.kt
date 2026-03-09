@file:OptIn(StonecutterInternalAPI::class)

package dev.kikugie.stonecutter.build.data

import dev.kikugie.stitcher.issue.ProblemReport
import dev.kikugie.stonecutter.AnyVersion
import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.controller.StonecutterControllerExtension
import dev.kikugie.stonecutter.controller.flag.StonecutterFlag
import dev.kikugie.stonecutter.util.newInstance
import dev.kikugie.stitcher.replacement.RegexReplacement
import dev.kikugie.stitcher.replacement.Replacement
import dev.kikugie.stitcher.replacement.ReplacementBuilder
import dev.kikugie.stitcher.replacement.StringReplacement
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import javax.inject.Inject

internal abstract class StonecutterBuildData @Inject constructor(
    controller: StonecutterControllerExtension,
    current: Identifier,
    providers: ProviderFactory
) {
    @get:Input abstract val constants: MapProperty<Identifier, Boolean>
    @get:Input abstract val swaps: MapProperty<Identifier, String>
    @get:Input abstract val dependencies: MapProperty<Identifier, AnyVersion>

    @get:Nested abstract val stringReplacements: ListProperty<StringReplacementSpec>
    @get:Nested abstract val regexReplacements: ListProperty<RegexReplacementSpec>

    @get:Internal internal abstract val dummyDependencies: MapProperty<Identifier, AnyVersion>

    @get:Inject protected abstract val objects: ObjectFactory

    private val stringReplacementBuilder: ReplacementBuilder<StringReplacement> = ReplacementBuilder.string()
    private val regexReplacementBuilder: ReplacementBuilder<RegexReplacement> = ReplacementBuilder.regex()

    init {
        constants.set(mutableMapOf())
        swaps.set(mutableMapOf())
        dependencies.set(providers.provider { patchImplicitDependency(dummyDependencies, controller.flags[StonecutterFlag.IMPLICIT_RECEIVER], current) })

        dummyDependencies.set(mutableMapOf())

        stringReplacements.set(providers.provider { stringReplacementBuilder.build().map(objects::stringSpec) })
        regexReplacements.set(providers.provider { regexReplacementBuilder.build().map(objects::regexSpec) })
    }

    internal fun addString(repl: StringReplacement) {
        stringReplacementBuilder.add(repl)?.thr()
    }
    internal fun addRegex(repl: RegexReplacement) {
        regexReplacementBuilder.add(repl)?.thr()
    }

    interface StringReplacementSpec {
        @get:Input val target: Property<String>
        @get:Input val sources: SetProperty<String>
        @get:Input @get:Optional val identifier: Property<Identifier>
        @get:Input @get:Optional val tags: SetProperty<Replacement.Tag>

        fun build(): StringReplacement =
            StringReplacement(target.get(), sources.get(), identifier.orNull, tags.getOrElse(emptySet()))
    }

    interface RegexReplacementSpec {
        @get:Input val target: Property<String>
        @get:Input val pattern: Property<String>
        @get:Input @get:Optional val identifier: Property<Identifier>
        @get:Input @get:Optional val tags: SetProperty<Replacement.Tag>

        fun build(): RegexReplacement =
            RegexReplacement(target.get(), pattern.get(), identifier.orNull, tags.getOrElse(emptySet()))
    }
}

private fun ObjectFactory.stringSpec(repl: StringReplacement): StonecutterBuildData.StringReplacementSpec =
    newInstance { target.set(repl.target); sources.set(repl.sources); identifier.set(repl.identifier); tags.addAll(repl.tags) }

private fun ObjectFactory.regexSpec(repl: RegexReplacement): StonecutterBuildData.RegexReplacementSpec =
    newInstance { target.set(repl.target); pattern.set(repl.pattern); identifier.set(repl.identifier); tags.addAll(repl.tags) }

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

private fun ProblemReport.thr(): Nothing = throw GradleException("$label:\n$details")