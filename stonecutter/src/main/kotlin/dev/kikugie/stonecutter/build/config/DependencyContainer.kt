package dev.kikugie.stonecutter.build.config

import dev.kikugie.commons.then
import dev.kikugie.semver.impl.VersionParsingException
import dev.kikugie.stitcher.util.IDENTIFIER_PATTERN
import dev.kikugie.stitcher.util.isValidIdentifier
import dev.kikugie.stonecutter.AnyVersion
import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.data.version.LenientOperations
import dev.kikugie.stonecutter.data.version.asIllegalArg
import dev.kikugie.stonecutter.data.whatever.ExtendedMapProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import javax.inject.Inject

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class DependencyDsl

/**[Stonecutter dependency](https://stonecutter.kikugie.dev/wiki/config/params#condition-dependencies) configuration extension.*/
@DependencyDsl
public abstract class DependencyContainer @Inject constructor(private val delegate: MapProperty<Identifier, AnyVersion>) :
    ExtendedMapProperty<Identifier, AnyVersion>, MapProperty<Identifier, AnyVersion> by delegate {
    /**
     * Defines the [key] as a dependency with the provided [value].
     *
     * @param key Dependency key matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     * @param value Version string that can be parsed as either
     *        [SemanticVersion][dev.kikugie.semver.data.SemanticVersion] or
     *        [StringVersion][dev.kikugie.semver.data.StringVersion]
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#dependency-specification">Dependency Documentation</a>
     */
    override fun put(key: Identifier, value: AnyVersion): Unit =
        check(key, value) then delegate.put(key, value)

    /**
     * Defines a dependency by the [key] with the value lazily provided by [providerOfValue].
     *
     * @param key Dependency key matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     * @param providerOfValue Provider supplying a version that can be parsed as either
     *        [SemanticVersion][dev.kikugie.semver.data.SemanticVersion] or
     *        [StringVersion][dev.kikugie.semver.data.StringVersion]
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#dependency-specification">Dependency Documentation</a>
     */
    override fun put(key: Identifier, providerOfValue: Provider<out AnyVersion>): Unit =
        check(key) then delegate.put(key, providerOfValue.map { check(key, it); it })

    /**
     * Defines multiple dependencies from the provided [entries].
     *
     * @param entries Map of dependencies with keys matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     *        and values that can be parsed as either
     *        [SemanticVersion][dev.kikugie.semver.data.SemanticVersion] or
     *        [StringVersion][dev.kikugie.semver.data.StringVersion]
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#dependency-specification">Dependency Documentation</a>
     */
    override fun putAll(entries: Map<out Identifier, AnyVersion>): Unit =
        entries.forEach(::check) then delegate.putAll(entries)

    /**
     * Defines multiple dependencies from the map lazily provided by [provider].
     *
     * @param provider Provider supplying a map of dependencies with keys matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     *        and values that can be parsed as either
     *        [SemanticVersion][dev.kikugie.semver.data.SemanticVersion] or
     *        [StringVersion][dev.kikugie.semver.data.StringVersion]
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#dependency-specification">Dependency Documentation</a>
     */
    override fun putAll(provider: Provider<out Map<out Identifier, AnyVersion>>): Unit =
        delegate.putAll(provider.map { it.forEach(::check); it })

    private fun check(entry: Map.Entry<String, AnyVersion>): Unit =
        check(entry.key, entry.value)

    private fun check(key: String, value: AnyVersion? = null) {
        require(key.isNotBlank()) { "Dependency key '$key' cannot be blank" }
        require(key.isValidIdentifier()) { "Dependency key '$key' must match the pattern `$IDENTIFIER_PATTERN`"}
        if (value != null) LenientOperations.tryParse(value).getOrElse {
            it as VersionParsingException
            throw it.asIllegalArg(value, message = "${it.message} (for dependency '$key')")
        }
    }
}