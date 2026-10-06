package dev.kikugie.stonecutter.build.config

import dev.kikugie.stonecutter.VersionString
import dev.kikugie.commons.then
import dev.kikugie.semver.impl.VersionParsingException
import dev.kikugie.stonecutter.AnyVersion
import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.TargetID
import dev.kikugie.stonecutter.data.collection.ExtendedMapProperty
import dev.kikugie.stonecutter.data.version.LenientOperations
import dev.kikugie.stonecutter.data.version.asIllegalArg
import dev.kikugie.stonecutter.util.IDENTIFIER_PATTERN
import dev.kikugie.stonecutter.util.isValidIdentifier
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import javax.inject.Inject

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class TargetDsl

/**[Stonecutter target](https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#targets) configuration extension.*/
@TargetDsl
public abstract class TargetContainer @Inject constructor(private val delegate: MapProperty<Identifier, AnyVersion>) :
    ExtendedMapProperty<@TargetID Identifier, @VersionString AnyVersion>, MapProperty<@TargetID Identifier, @VersionString AnyVersion> by delegate {
    /**
     * Defines the [key] as a target with the provided [value].
     *
     * @param key Target key matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     * @param value Version string that can be parsed as either
     *        [SemanticVersion][dev.kikugie.semver.data.SemanticVersion] or
     *        [StringVersion][dev.kikugie.semver.data.StringVersion]
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#defining-targets">Target Documentation</a>
     */
    override fun put(key: @TargetID Identifier, value: @VersionString AnyVersion): Unit =
        check(key, value) then delegate.put(key, value)

    /**
     * Defines a target by the [key] with the value lazily provided by [providerOfValue].
     *
     * @param key Target key matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     * @param providerOfValue Provider supplying a version that can be parsed as either
     *        [SemanticVersion][dev.kikugie.semver.data.SemanticVersion] or
     *        [StringVersion][dev.kikugie.semver.data.StringVersion]
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#defining-targets">Target Documentation</a>
     */
    override fun put(key: @TargetID Identifier, providerOfValue: Provider<out @VersionString AnyVersion>): Unit =
        check(key) then delegate.put(key, providerOfValue.map { check(key, it); it })

    /**
     * Defines multiple targets from the provided [entries].
     *
     * @param entries Map of targets with keys matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     *        and values that can be parsed as either
     *        [SemanticVersion][dev.kikugie.semver.data.SemanticVersion] or
     *        [StringVersion][dev.kikugie.semver.data.StringVersion]
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#defining-targets">Target Documentation</a>
     */
    override fun putAll(entries: Map<out @TargetID Identifier, @VersionString AnyVersion>): Unit =
        entries.forEach(::check) then delegate.putAll(entries)

    /**
     * Defines multiple targets from the map lazily provided by [provider].
     *
     * @param provider Provider supplying a map of targets with keys matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     *        and values that can be parsed as either
     *        [SemanticVersion][dev.kikugie.semver.data.SemanticVersion] or
     *        [StringVersion][dev.kikugie.semver.data.StringVersion]
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#defining-targets">Target Documentation</a>
     */
    override fun putAll(provider: Provider<out Map<out @TargetID Identifier, @VersionString AnyVersion>>): Unit =
        delegate.putAll(provider.map { it.forEach(::check); it })

    private fun check(entry: Map.Entry<String, AnyVersion>): Unit =
        check(entry.key, entry.value)

    private fun check(key: String, value: AnyVersion? = null) {
        require(key.isNotBlank()) { "Target key '$key' cannot be blank" }
        require(key.isValidIdentifier()) { "Target key '$key' must match the pattern `$IDENTIFIER_PATTERN`" }
        if (value != null) LenientOperations.tryParse(value).getOrElse {
            it as VersionParsingException
            throw it.asIllegalArg(value, message = "${it.message} (for target '$key')")
        }
    }
}
