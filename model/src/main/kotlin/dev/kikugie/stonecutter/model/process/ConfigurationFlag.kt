package dev.kikugie.stonecutter.model.process

import dev.kikugie.stonecutter.model.Identifier

public class ConfigurationFlag<T : Any> @PublishedApi internal constructor(public val key: Identifier, public val default: T) {
    override fun equals(other: Any?): Boolean = this === other || other is ConfigurationFlag<*> && key == other.key
    override fun hashCode(): Int = key.hashCode()

    @Suppress("NOTHING_TO_INLINE")
    public companion object {
        @JvmStatic @JvmName("of")
        public inline operator fun invoke(key: Identifier, value: Boolean): ConfigurationFlag<Boolean> =
            ConfigurationFlag(key, value)

        @JvmStatic @JvmName("of")
        public inline operator fun invoke(key: Identifier, value: Int): ConfigurationFlag<Long> =
            ConfigurationFlag(key, value.toLong())

        @JvmStatic @JvmName("of")
        public inline operator fun invoke(key: Identifier, value: Long): ConfigurationFlag<Long> =
            ConfigurationFlag(key, value)

        @JvmStatic @JvmName("of")
        public inline operator fun invoke(key: Identifier, value: Float): ConfigurationFlag<Double> =
            ConfigurationFlag(key, value.toDouble())

        @JvmStatic @JvmName("of")
        public inline operator fun invoke(key: Identifier, value: Double): ConfigurationFlag<Double> =
            ConfigurationFlag(key, value)

        @JvmStatic @JvmName("of")
        public inline operator fun invoke(key: Identifier, value: CharSequence): ConfigurationFlag<String> =
            ConfigurationFlag(key, value.toString())

        @JvmStatic @JvmName("of")
        public inline operator fun invoke(key: Identifier, value: String): ConfigurationFlag<String> =
            ConfigurationFlag(key, value)
    }
}
