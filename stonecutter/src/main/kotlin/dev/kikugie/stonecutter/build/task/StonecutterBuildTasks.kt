package dev.kikugie.stonecutter.build.task

import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.data.collection.TaskProviderMap
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class BuildTasksDsl

/**Extension interface providing access to Stonecutter file processing tasks.*/
@BuildTasksDsl
public interface StonecutterBuildTasks {
    /**
     * Tasks responsible for preprocessing files for each [SourceSet] and combining
     * them with unchanged files, minus the local version overrides.
     *
     * The generated files are used as sources for multi-project builds
     * and are stored in [generatedSourcesDir].
     *
     * These tasks should be considered an internal implementation
     * and only be configured directly in rare edge cases.
     */
    @StonecutterInternalAPI
    public val generate: TaskProviderMap<StonecutterGenerateTask>

    /**
     * Tasks responsible for copying [generated sources][generate]
     * into the shared source directory when switching the active version.
     *
     * These tasks should be considered an internal implementation
     * and only be configured directly in rare edge cases.
     */
    @StonecutterInternalAPI
    public val merge: TaskProviderMap<Copy>

    /**`build/generated/stonecutter/`*/
    public val generatedSourcesDir: DirectoryProperty

    /**Adds the generated directories to the provided [src].*/
    public fun configureSource(src: SourceSet)
}