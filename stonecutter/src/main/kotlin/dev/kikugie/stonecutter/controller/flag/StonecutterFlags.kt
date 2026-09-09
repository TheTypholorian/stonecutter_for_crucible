package dev.kikugie.stonecutter.controller.flag

import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import kotlin.annotation.AnnotationRetention.BINARY

@DslMarker @Retention(BINARY)
private annotation class FlagDsl

/**
 * Extension interface to provide read-write access to [StonecutterFlag]s.
 * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-controller#flags">Flags Documentation</a>
 */
@FlagDsl
public interface StonecutterFlags {
    /**
     * Returns the value of the flag with the given [key].
     * @throws IllegalStateException If no flag is registered under the [key]
     */
    public operator fun get(key: String): Any = get(StonecutterFlag.named(key))

    /** Returns the value of the given [key] flag. */
    public operator fun <T : Any> get(key: StonecutterFlag<T>): T

    /** Returns the value of this flag. */
    public operator fun <T : Any> StonecutterFlag<T>.invoke(): T = get(this)

    /**
     * Assigns the [value] to the flag with the given [key].
     * @throws IllegalStateException If no flag is registered under the [key]
     * @throws IllegalArgumentException If the [value] type doesn't match the flag's
     */
    public operator fun set(key: String, value: Any): Unit = StonecutterFlag.named(key).let {
        require(value::class == it.default::class) { "Value must be ${it.default::class.simpleName} for flag '$key'" }
        @Suppress("UNCHECKED_CAST") set(it as StonecutterFlag<Any>, value)
    }

    /** Assigns the [value] to the given [key] flag. */
    public operator fun <T : Any> set(key: StonecutterFlag<T>, value: T)

    /** Assigns the [value] to this flag. */
    public operator fun <T : Any> StonecutterFlag<T>.invoke(value: T): Unit = set(this, value)

    /** Assigns the [value] to this flag, enabling the `FLAG = value` syntax. */
    public fun <T : Any> StonecutterFlag<T>.assign(value: T): Unit = set(this, value)
}

internal class StonecutterFlagsImpl(
    val registry: ConfigurationRegistry = ConfigurationRegistry(),
    private val scope: FlagScope = FlagScope.CONTROLLER,
    private val provider: (StonecutterFlag<*>) -> String?,
) : StonecutterFlags {
    init {
        for (flag in StonecutterFlag.registered(scope)) fill(flag)
    }

    private fun <T : Any> fill(flag: StonecutterFlag<T>) {
        registry[flag.configuration] = provider(flag)?.let(flag::fromString) ?: flag.default
    }

    override fun <T : Any> get(key: StonecutterFlag<T>): T = when {
        key.scope == scope || key.scope == FlagScope.LOCAL -> registry[key.configuration]
        else -> provider(key)?.let(key::fromString) ?: key.default
    }

    override fun <T : Any> set(key: StonecutterFlag<T>, value: T) {
        require(key.scope == scope || key.scope == FlagScope.LOCAL) {
            "Flag '${key.key}' is ${key.scope.name.lowercase()}-scoped and cannot be assigned in a ${scope.name.lowercase()}-scoped context"
        }
        registry[key.configuration] = value
    }
}