package dev.kikugie.stonecutter.build.config

import dev.kikugie.stonecutter.SourceSetPattern

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class FilesDsl

/**
 * Configures which files are processed and commented out.
 */
@FilesDsl
public sealed interface StonecutterFiles : PatternFilter {
    /**
     * Marks files matching [patterns] as disabled if [direction] is `true`.
     *
     * Disabled files are commented out and excluded from compilation.
     * Each pattern is relative to each `src/.../` directory.
     */
    public fun disable(direction: Boolean, @SourceSetPattern vararg patterns: String): Unit =
        disable(direction, patterns.asIterable())

    /**
     * Marks files matching [patterns] as disabled if [direction] is `true`.
     *
     * Disabled files are commented out and excluded from compilation.
     * Each pattern is relative to each `src/.../` directory.
     */
    public fun disable(direction: Boolean, @SourceSetPattern patterns: Iterable<String>)
}
