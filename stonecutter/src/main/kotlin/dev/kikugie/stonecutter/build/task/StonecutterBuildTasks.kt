package dev.kikugie.stonecutter.build.task

import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.data.whatever.TaskProviderMap
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.Sync

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class BuildTasksDsl

/**Extension interface providing access to Stonecutter file processing tasks.*/
@BuildTasksDsl
public interface StonecutterBuildTasks {
    /**
     * Tasks responsible for preprocessing files for each [SourceSet].
     *
     * The processed files are stored in the [processedCacheDir].
     *
     * These tasks should be considered an internal implementation
     * and only be configured directly in rare edge cases.
     */
    @StonecutterInternalAPI
    public val prepare: TaskProviderMap<StonecutterPrepareTask>

    /**
     * Tasks responsible for combining [preprocessed sources][prepare]
     * with unchanged and overridden files for each [SourceSet].
     *
     * The combined files are used as generated sources for multi-project builds.
     * The files are stored in [generatedSourcesDir].
     *
     * These tasks should be considered an internal implementation
     * and only be configured directly in rare edge cases.
     */
    @StonecutterInternalAPI
    public val generate: TaskProviderMap<Sync>

    /**
     * Tasks responsible for copying [generated sources][generate]
     * into the shared source directory when switching the active version.
     *
     * These tasks should be considered an internal implementation
     * and only be configured directly in rare edge cases.
     */
    public val merge: TaskProviderMap<Copy>

    /**`build/stonecutter-cache/`*/
    public val processedCacheDir: DirectoryProperty

    /**`build/generated/stonecutter/`*/
    public val generatedSourcesDir: DirectoryProperty

    /**Adds the generated directories to the provided [src].*/
    public fun configureSource(src: SourceSet)
}