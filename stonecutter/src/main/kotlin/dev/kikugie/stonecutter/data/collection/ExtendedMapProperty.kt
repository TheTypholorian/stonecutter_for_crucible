package dev.kikugie.stonecutter.data.collection

import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider

/**[MapProperty] extension, adding Kotlin operator functions.*/
public interface ExtendedMapProperty<K : Any, V : Any> : MapProperty<K, V> {
    /**Adds the [key] to [value] entry to the map.*/
    public operator fun set(key: K, value: V) {
        put(key, value)
    }

    /**Adds the [key] to [provider] entry to the map.*/
    public operator fun set(key: K, provider: Provider<V>) {
        put(key, provider)
    }

    /**Returns the value stored under the [key], or `null` if it's absent.*/
    public operator fun get(key: K): V? =
        getting(key).orNull
}