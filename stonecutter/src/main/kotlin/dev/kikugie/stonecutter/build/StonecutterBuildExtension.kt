package dev.kikugie.stonecutter.build

import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.semver.data.Version
import dev.kikugie.stonecutter.build.config.*
import dev.kikugie.stonecutter.build.task.StonecutterBuildTasks
import dev.kikugie.stonecutter.controller.flag.StonecutterFlags
import dev.kikugie.stonecutter.data.StonecutterProject
import dev.kikugie.stonecutter.data.property.StructuredPropertiesProvider
import dev.kikugie.stonecutter.data.tree.ProjectBranch
import dev.kikugie.stonecutter.data.tree.ProjectNode
import dev.kikugie.stonecutter.data.tree.ProjectTree
import dev.kikugie.stonecutter.data.version.SemanticOperations
import dev.kikugie.stonecutter.data.version.VersionOperations
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.util.PatternFilterable
import java.io.File

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class BuildDsl

/**
 * Extension interface applied to versioned Stonecutter buildscripts.
 *
 * The extension provides configuration options for parameters using in file preprocessing,
 * as well as utilities for configuring version-specific buildscript actions.
 *
 * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/guides/start/build-setup">Build Utilities Documentation</a>
 * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/">Preprocessor Configuration Documentation</a>
 */
@BuildDsl
public interface StonecutterBuildExtension : VersionOperations<Version>, StructuredPropertiesProvider {
    /**
     * Gradle project this extension is applied to.
     */
    public val project: Project

    /**
     * Node associated with the applied Gradle [Project][org.gradle.api.Project].
     *
     * Project nodes provide type-safe navigation of the sibling projects,
     * and can be safely used in task configuration as they don't
     * contain Gradle [Project][org.gradle.api.Project] references.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-model#node">Project Node Documentation</a>
     */
    public val node: ProjectNode

    /**
     * Branch associated with the [node].
     *
     * Project branches are used to group versioned projects by module or mod loader.
     * In standard use cases the root branch is used, which points to the same project
     * as the [tree].
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-model#branch">Project Branch Documentation</a>
     */
    public val branch: ProjectBranch get() = node.branch

    /**
     * Tree associated with the [node].
     *
     * The project tree corresponds to the subprojects initially registered
     * with `stonecutter.create()` in `settings.gradle(.kts)`.
     * The tree instance provides access to shared properties, such as
     * the active and VCS versions and all existing nodes.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-model#tree">Project Tree Documentation</a>
     */
    public val tree: ProjectTree get() = branch.tree

    /**
     * Project descriptor of the active version assigned in `stonecutter.gradle(.kts)`.
     *
     * The active subproject has its sources linked to the shared `src/` directory.
     * This property follows the implication:
     * ```kotlin
     * assert(sc.current.isActive == (sc.current == sc.active))
     * ```
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-controller#active-version">Active Version Documentation</a>
     */
    public val active: StonecutterProject? get() = tree.current

    /**
     * Project descriptor of the applied Gradle [Project][org.gradle.api.Project].
     *
     * The descriptor provides quick access to the project's last path segment
     * and the registered associated version.
     * It is often used to add conditional behaviour to the build script:
     * ```kotlin
     * if (sc.current.isActive) {
     *     // ...
     * }
     *
     * if (sc.current.parsed >= "2.0") {
     *     // ...
     * }
     * ```
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/guides/start/build-setup#version-aware-buildscript">Version-aware Buildscript Documentation</a>
     */
    public val current: StonecutterProject get() = node.metadata

    /**
     * Set of all project descriptors in the [branch].
     */
    public val versions: Collection<StonecutterProject> get() = branch.versions

    /**
     * Accessor for strictly SemVer version evaluation.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/guides/start/build-setup#version-aware-buildscript">Semantic Operations Documentation</a>
     */
    public val semantics: VersionOperations<SemanticVersion> get() = SemanticOperations

    /**
     * Swap configuration extension.
     * 
     * Swaps provide ways to substitute code fragments with predefined templates during file preprocessing.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/swaps">Swaps Documentation</a>
     */
    public val swaps: SwapContainer

    /**
     * Constant configuration extension.
     *
     * Constants define keywords used in preprocessed conditions that evaluate to a predefined boolean value.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#constants">Constants Documentation</a>
     */
    public val constants: ConstantContainer

    /**
     * Target configuration extension.
     *
     * Targets define named versions, which can be checked against predicates in preprocessed conditions.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#targets">Targets Documentation</a>
     */
    public val targets: TargetContainer

    /**
     * Target configuration extension.
     *
     * Targets define named versions, which can be checked against predicates in preprocessed conditions.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#targets">Targets Documentation</a>
     */
    @Deprecated("Stonecutter dependencies were renamed to targets", ReplaceWith("targets"))
    public val dependencies: DependencyContainer

    /**
     * Replacement configuration extension.
     *
     * Replacements define string and regex patterns that are substituted with provided values across all files.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements">Replacements Documentation</a>
     */
    public val replacements: ReplacementContainer

    /**
     * Per-[SourceSet][org.gradle.api.tasks.SourceSet] file filters.
     *
     * Filters are used to exclude files from the preprocessing step.
     * Patterns are resolved relative to each `src/.../` directory.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/custom-formats#file-filtering">File Filter Documentation</a>
     */
    public val filters: PatternFilterable

    /**
     * Per-node view of Stonecutter configuration flags.
     *
     * [Controller-scoped][dev.kikugie.stonecutter.controller.flag.FlagScope.CONTROLLER] flags
     * are read-only here — they are configured in `stonecutter.gradle(.kts)`.
     * [Node-scoped][dev.kikugie.stonecutter.controller.flag.FlagScope.NODE] flags are assignable
     * and apply to this node only.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-controller#flags">Flags Documentation</a>
     */
    public val flags: StonecutterFlags

    /**
     * Stonecutter file processing task access extension.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-controller#task-hooks">Task Hooks Documentation</a>
     */
    public val tasks: StonecutterBuildTasks

    /**
     * Configures the swaps extension.
     *
     * Swaps provide ways to substitute code fragments with predefined templates during file preprocessing.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/swaps">Swaps Documentation</a>
     */
    public infix fun swaps(action: Action<SwapContainer>): Unit = action.execute(swaps)

    /**
     * Configures the constants extension.
     *
     * Constants define keywords used in preprocessed conditions that evaluate to a predefined boolean value.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#constants">Constants Documentation</a>
     */
    public infix fun constants(action: Action<ConstantContainer>): Unit = action.execute(constants)

    /**
     * Configures the targets extension.
     *
     * Targets define named versions, which can be checked against predicates in preprocessed conditions.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#targets">Targets Documentation</a>
     */
    public infix fun targets(action: Action<TargetContainer>): Unit = action.execute(targets)

    /**
     * Configures the targets extension.
     *
     * Targets define named versions, which can be checked against predicates in preprocessed conditions.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#targets">Targets Documentation</a>
     */
    @Deprecated("Stonecutter dependencies were renamed to targets", ReplaceWith("targets(action)"))
    @Suppress("DEPRECATION")
    public infix fun dependencies(action: Action<DependencyContainer>): Unit = action.execute(dependencies)

    /**
     * Configures the replacements extension.
     *
     * Replacements define string and regex patterns that are substituted with provided values across all files.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements">Replacements Documentation</a>
     */
    public infix fun replacements(action: Action<ReplacementContainer>): Unit = action.execute(replacements)

    /**
     * Executes the [action] in context of this node's Stonecutter flags.
     *
     * [Controller-scoped][dev.kikugie.stonecutter.controller.flag.FlagScope.CONTROLLER] flags
     * are read-only; [node-scoped][dev.kikugie.stonecutter.controller.flag.FlagScope.NODE] flags
     * can be assigned with the `=` operator and apply to this node only.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-controller#flags">Flags Documentation</a>
     */
    public infix fun flags(action: Action<StonecutterFlags>): Unit = action.execute(flags)

    /**Configures the [tasks] extension.*/
    public infix fun tasks(action: Action<StonecutterBuildTasks>): Unit = action.execute(tasks)

    /**
     * Processes the [file] using existing handlers and configuration,
     * writing the result to [destination] relative to the project directory.
     *
     * **This function is computationally expensive and should be used sparingly.**
     *
     * @return The processed file location
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/guides/tips/resource-processing">Resource Processing Guide</a>
     */
    public fun process(file: File, destination: String): File
}