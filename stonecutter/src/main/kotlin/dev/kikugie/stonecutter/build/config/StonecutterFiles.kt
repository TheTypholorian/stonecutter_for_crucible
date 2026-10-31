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
     * File patterns explicitly included in processing.
     *
     * Adding files contributes to the default filter set
     * based on extensions registered in [FileHandlerContainer][dev.kikugie.stonecutter.controller.file.FileHandlerContainer].
     */
    override var includes: Set<String>

    /**
     * File patterns explicitly excluded from processing.
     *
     * Excluded files are copied to generated sources as-is,
     * which may be useful if they contain syntax Stonecutter can't correctly process.
     */
    override var excludes: Set<String>

    /**
     * Marks files matching [patterns] as disabled if [direction] is `true`,
     * or as enabled if it's `false`.
     *
     * Disabled files are commented out and excluded from compilation.
     * Each pattern is relative to each `src/.../` directory.
     */
    public fun disable(direction: Boolean, @SourceSetPattern vararg patterns: String): Unit =
        disable(direction, patterns.asIterable())

    /**
     * Marks files matching [patterns] as disabled if [direction] is `true`,
     * or as enabled if it's `false`.
     *
     * Disabled files are commented out and excluded from compilation.
     * Each pattern is relative to each `src/.../` directory.
     */
    public fun disable(direction: Boolean, @SourceSetPattern patterns: Iterable<String>)

    /**
     * File patterns explicitly included in processing.
     * Calls to this function are cumulative.
     *
     * Adding files contributes to the default filter set
     * based on extensions registered in [FileHandlerContainer][dev.kikugie.stonecutter.controller.file.FileHandlerContainer].
     */
    override fun include(vararg patterns: String)

    /**
     * File patterns explicitly included in processing.
     * Calls to this function are cumulative.
     *
     * Adding files contributes to the default filter set
     * based on extensions registered in [FileHandlerContainer][dev.kikugie.stonecutter.controller.file.FileHandlerContainer].
     */
    override fun include(patterns: Iterable<String>)

    /**
     * File patterns explicitly excluded from processing.
     * Calls to this function are cumulative.
     *
     * Excluded files are copied to generated sources as-is,
     * which may be useful if they contain syntax Stonecutter can't correctly process.
     */
    override fun exclude(vararg patterns: String)

    /**
     * File patterns explicitly excluded from processing.
     * Calls to this function are cumulative.
     *
     * Excluded files are copied to generated sources as-is,
     * which may be useful if they contain syntax Stonecutter can't correctly process.
     */
    override fun exclude(patterns: Iterable<String>)
}
