package dev.kikugie.stonecutter.controller.flag

import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import kotlin.annotation.AnnotationRetention.BINARY

@DslMarker @Retention(BINARY)
private annotation class FlagDsl

/**Extension interface to provide read-only access to [StonecutterFlag]s.*/
@FlagDsl
public sealed interface StonecutterFlagsView {
    public operator fun get(key: String): Any = get(StonecutterFlag.named(key))
    public operator fun <T : Any> get(key: StonecutterFlag<T>): T
    public operator fun <T : Any> StonecutterFlag<T>.invoke(): T = get(this)
}

/**Extension interface to provide read-write access to [StonecutterFlag]s.*/
@FlagDsl
public sealed interface StonecutterFlags : StonecutterFlagsView {
    public operator fun set(key: String, value: Any): Unit = StonecutterFlag.named(key).let {
        require(value::class == it.default::class) { "Value must be ${it.default::class.simpleName} for flag '$key'" }
        @Suppress("UNCHECKED_CAST") set(it as StonecutterFlag<Any>, value)
    }
    public operator fun <T : Any> set(key: StonecutterFlag<T>, value: T)
    public operator fun <T : Any> StonecutterFlag<T>.invoke(value: T): Unit = set(this, value)
}

internal class StonecutterFlagsImpl(
    val registry: ConfigurationRegistry = ConfigurationRegistry(),
    private val provider: (StonecutterFlag<*>) -> String? = { null },
) : StonecutterFlags {
    private val assigned: MutableSet<StonecutterFlag<*>> = mutableSetOf()

    val serializable: Map<String, String>
        get() = assigned.associate { it.key to registry[it.configuration].toString() }

    override fun <T : Any> get(key: StonecutterFlag<T>): T =
        if (key in assigned) registry[key.configuration]
        else provider(key)?.let(key::fromString) ?: key.default

    override fun <T : Any> set(key: StonecutterFlag<T>, value: T) {
        assigned += key
        registry[key.configuration] = value
    }
}