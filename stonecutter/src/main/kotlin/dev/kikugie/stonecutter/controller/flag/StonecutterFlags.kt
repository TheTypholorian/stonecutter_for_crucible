package dev.kikugie.stonecutter.controller.flag

import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import kotlin.annotation.AnnotationRetention.BINARY

@DslMarker @Retention(BINARY)
private annotation class FlagDsl

/**Extension interface to provide read-write access to [StonecutterFlag]s.*/
@FlagDsl
public interface StonecutterFlags {
    public operator fun get(key: String): Any = get(StonecutterFlag.named(key))
    public operator fun <T : Any> get(key: StonecutterFlag<T>): T
    public operator fun <T : Any> StonecutterFlag<T>.invoke(): T = get(this)

    public operator fun set(key: String, value: Any): Unit = StonecutterFlag.named(key).let {
        require(value::class == it.default::class) { "Value must be ${it.default::class.simpleName} for flag '$key'" }
        @Suppress("UNCHECKED_CAST") set(it as StonecutterFlag<Any>, value)
    }
    public operator fun <T : Any> set(key: StonecutterFlag<T>, value: T)
    public operator fun <T : Any> StonecutterFlag<T>.invoke(value: T): Unit = set(this, value)

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