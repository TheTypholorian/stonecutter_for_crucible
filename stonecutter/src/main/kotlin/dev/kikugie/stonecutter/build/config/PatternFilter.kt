package dev.kikugie.stonecutter.build.config

import org.gradle.api.tasks.util.PatternSet

/**
 * Ant-style path patterns selecting the files a configuration applies to.
 *
 * Patterns are resolved relative to each `src/.../` directory.
 * A filter with no patterns matches every file.
 *
 * This interface mirrors the functionality of [PatternFilterable][org.gradle.api.tasks.util.PatternFilterable],
 * but doesn't allow [FileTreeElement][org.gradle.api.file.FileTreeElement] specs, as those can't be serialized.
 */
public interface PatternFilter {
    /** Patterns of the files included by this filter. Empty includes everything. */
    public var includes: Set<String>

    /** Patterns of the files excluded by this filter, applied after [includes]. */
    public var excludes: Set<String>

    /** Adds [patterns] to [includes]. */
    public fun include(vararg patterns: String)

    /** Adds [patterns] to [includes]. */
    public fun include(patterns: Iterable<String>)

    /** Adds [patterns] to [excludes]. */
    public fun exclude(vararg patterns: String)

    /** Adds [patterns] to [excludes]. */
    public fun exclude(patterns: Iterable<String>)
}

internal class PatternSetFilter(val patterns: PatternSet = PatternSet()) : PatternFilter {
    override var includes: Set<String>
        get() = patterns.includes
        set(value) { patterns.setIncludes(value) }

    override var excludes: Set<String>
        get() = patterns.excludes
        set(value) { patterns.setExcludes(value) }

    override fun include(vararg patterns: String) { this.patterns.include(*patterns) }
    override fun include(patterns: Iterable<String>) { this.patterns.include(patterns) }
    override fun exclude(vararg patterns: String) { this.patterns.exclude(*patterns) }
    override fun exclude(patterns: Iterable<String>) { this.patterns.exclude(patterns) }
}
