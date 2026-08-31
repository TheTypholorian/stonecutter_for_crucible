package dev.kikugie.stonecutter.build.config

import dev.kikugie.commons.then
import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.SwapID
import dev.kikugie.stonecutter.data.whatever.ExtendedMapProperty
import dev.kikugie.stonecutter.util.IDENTIFIER_PATTERN
import dev.kikugie.stonecutter.util.isValidIdentifier
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import javax.inject.Inject

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class SwapDsl

/**[Stonecutter swap](https://stonecutter.kikugie.dev/wiki/config/params#string-swaps) configuration extension.*/
@SwapDsl
public abstract class SwapContainer @Inject constructor(private val delegate: MapProperty<Identifier, String>) :
    ExtendedMapProperty<@SwapID Identifier, String>, MapProperty<@SwapID Identifier, String> by delegate {

    /**
     * Defines a swap entry by the [key] with the provided [value].
     *
     * @param key Swap key matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#swap-specification">Swap Documentation</a>
     */
    override fun put(key: @SwapID Identifier, value: String): Unit =
        check(key) then delegate.put(key, value)

    /**
     * Defines a swap entry by the [key] with the value lazily provided by [providerOfValue].
     *
     * @param key Swap key matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#swap-specification">Swap Documentation</a>
     */
    override fun put(key: @SwapID Identifier, providerOfValue: Provider<out String>): Unit =
        check(key) then delegate.put(key, providerOfValue)

    /**
     * Defines multiple swap entries from the provided [entries].
     *
     * @param entries Map of swap entries with keys matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#swap-specification">Swap Documentation</a>
     */
    override fun putAll(entries: Map<out @SwapID Identifier, String>): Unit =
        entries.keys.forEach(::check) then delegate.putAll(entries)

    /**
     * Defines multiple swap entries from the map lazily provided by [provider].
     *
     * @param provider Provider supplying a map of swap entries with keys matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#swap-specification">Swap Documentation</a>
     */
    override fun putAll(provider: Provider<out Map<out @SwapID Identifier, String>>): Unit =
        delegate.putAll(provider.map { it.keys.forEach(::check); it })

    private fun check(key: Identifier) {
        require(key.isNotBlank()) { "Swap key '$key' cannot be blank" }
        require(key.isValidIdentifier()) { "Swap key '$key' must match the pattern `$IDENTIFIER_PATTERN`"}
    }
}