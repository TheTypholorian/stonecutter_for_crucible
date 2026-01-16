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
    override fun put(key: Identifier, value: AnyVersion): Unit =
        check(key, value) then delegate.put(key, value)

    override fun put(key: Identifier, providerOfValue: Provider<out AnyVersion>): Unit =
        check(key) then delegate.put(key, providerOfValue.map { check(key, it); it })

    override fun putAll(entries: Map<out Identifier, AnyVersion>): Unit =
        entries.forEach(::check) then delegate.putAll(entries)

    override fun putAll(provider: Provider<out Map<out Identifier, AnyVersion>>): Unit =
        delegate.putAll(provider.map { it.forEach(::check); it })

    private fun check(entry: Map.Entry<String, AnyVersion>) = check(entry.key, entry.value)
    private fun check(key: String, value: AnyVersion? = null) {
        require(key.isNotBlank()) { "Dependency key '$key' cannot be blank" }
        require(key.isValidIdentifier()) { "Dependency key '$key' must match the pattern `$IDENTIFIER_PATTERN`"}
        if (value != null) LenientOperations.tryParse(value).getOrElse {
            it as VersionParsingException
            throw it.asIllegalArg(value, message = "${it.message} (for dependency '$key')")
        }
    }
}