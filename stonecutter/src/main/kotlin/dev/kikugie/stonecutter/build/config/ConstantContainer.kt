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
private annotation class ConstantDsl

/**[Stonecutter constant](https://stonecutter.kikugie.dev/wiki/config/params#condition-constants) configuration extension.*/
@ConstantDsl
public abstract class ConstantContainer @Inject constructor(private val delegate: MapProperty<Identifier, Boolean>) :
    ExtendedMapProperty<Identifier, Boolean>, MapProperty<Identifier, Boolean> by delegate {
    /**
     * Defines all [choices] as constants, with their values set to `true` if they equal [sample].
     *
     * @param choices Constant keys matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     * @sample dev.kikugie.stonecutter.samples.constants.match_vararg
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#choice-selector">Constant Selector Documentation</a>
     */
    public fun match(sample: Identifier, vararg choices: Identifier): Unit = choices.forEach { put(it, it == sample) }

    /**
     * Defines all [choices] as constants, with their values set to `true` if they equal [sample].
     *
     * @param choices Constant keys matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     * @sample dev.kikugie.stonecutter.samples.constants.match_iter
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#choice-selector">Constant Selector Documentation</a>
     */
    public fun match(sample: Identifier, choices: Iterable<Identifier>): Unit = choices.forEach { put(it, it == sample) }

    /**
     * Defines a constant by the [key] with the provided [value].
     *
     * @param key Constant key matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#constant-specificationr">Constant Documentation</a>
     */
    override fun put(key: Identifier, value: Boolean): Unit =
        check(key) then delegate.put(key, value)

    /**
     * Defines a constant by the [key] with the value lazily provided by [providerOfValue].
     *
     * @param key Constant key matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#constant-specificationr">Constant Documentation</a>
     */
    override fun put(key: Identifier, providerOfValue: Provider<out Boolean>): Unit =
        check(key) then delegate.put(key, providerOfValue)

    /**
     * Defines multiple constants from the provided [entries].
     *
     * @param entries Constant map with keys matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#constant-specificationr">Constant Documentation</a>
     */
    override fun putAll(entries: Map<out Identifier, Boolean>): Unit =
        entries.keys.forEach(::check) then delegate.putAll(entries)

    /**
     * Defines multiple constants from the map lazily provided by [provider].
     *
     * @param provider Constant map provider with keys matching `[_a-zA-Z][_\-a-zA-Z0-9]*`
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#constant-specificationr">Constant Documentation</a>
     */
    override fun putAll(provider: Provider<out Map<out Identifier, Boolean>>): Unit =
        delegate.putAll(provider.map { it.keys.forEach(::check); it })

    private fun check(key: Identifier) {
        require(key.isNotBlank()) { "Constant key '$key' cannot be blank" }
        require(key.isValidIdentifier()) { "Constant key '$key' must match the pattern `$IDENTIFIER_PATTERN`" }
    }
}
