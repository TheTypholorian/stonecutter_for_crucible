package dev.kikugie.stonecutter.data

import dev.kikugie.stonecutter.AnyVersion
import dev.kikugie.stonecutter.Identifier
import kotlinx.serialization.Serializable

/**
 * Essential Stonecutter project metadata.
 *
 * @property project The Gradle project name in `versions/` directory
 * @property version The stringified project version, used for conditional evaluation
 * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-model#node">Project Node Documentation</a>
 */
@Serializable
public data class StonecutterProject(val project: Identifier, val version: AnyVersion) {
    /**
     * Wrapped parsed [version] used for comparisons.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#version-types">Version Types Documentation</a>
     */
    public val parsed: ParsedVersion by lazy { ParsedVersion(version) }

    /**
     * Whenever this project is assigned as active, with the shared `src/` directory linked to it.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-controller#active-version">Active Version Documentation</a>
     */
    public var isActive: Boolean = false
        internal set
}