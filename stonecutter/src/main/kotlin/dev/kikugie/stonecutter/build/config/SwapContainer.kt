package dev.kikugie.stonecutter.build.config

import dev.kikugie.commons.then
import dev.kikugie.stitcher.util.IDENTIFIER_PATTERN
import dev.kikugie.stitcher.util.isValidIdentifier
import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.data.whatever.ExtendedMapProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import javax.inject.Inject

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class SwapDsl

/**[Stonecutter swap](https://stonecutter.kikugie.dev/wiki/config/params#string-swaps) configuration extension.*/
@SwapDsl
public abstract class SwapContainer @Inject constructor(private val delegate: MapProperty<Identifier, String>) :
    ExtendedMapProperty<Identifier, String>, MapProperty<Identifier, String> by delegate {

    override fun put(key: Identifier, value: String): Unit =
        check(key) then delegate.put(key, value)

    override fun put(key: Identifier, providerOfValue: Provider<out String>): Unit =
        check(key) then delegate.put(key, providerOfValue)

    override fun putAll(entries: Map<out Identifier, String>): Unit =
        entries.keys.forEach(::check) then delegate.putAll(entries)

    override fun putAll(provider: Provider<out Map<out Identifier, String>>): Unit =
        delegate.putAll(provider.map { it.keys.forEach(::check); it })

    private fun check(key: Identifier) {
        require(key.isNotBlank()) { "Swap key '$key' cannot be blank" }
        require(key.isValidIdentifier()) { "Swap key '$key' must match the pattern `$IDENTIFIER_PATTERN`"}
    }
}