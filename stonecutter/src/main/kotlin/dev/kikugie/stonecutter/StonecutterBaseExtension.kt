package dev.kikugie.stonecutter

import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.stonecutter.VersionString.Companion.SEMVER_ONLY
import dev.kikugie.semver.data.Version
import dev.kikugie.stonecutter.controller.flag.StonecutterFlags
import dev.kikugie.stonecutter.data.property.StructuredPropertiesProvider
import dev.kikugie.stonecutter.data.version.SemanticOperations
import dev.kikugie.stonecutter.data.version.VersionOperations
import org.gradle.api.Action

public interface StonecutterBaseExtension : VersionOperations<Version>, StructuredPropertiesProvider {
    /**
     * Accessor for strictly SemVer version evaluation.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/guides/start/build-setup#version-aware-buildscript">Semantic Operations Documentation</a>
     */
    @VersionStringMeta(include = [SEMVER_ONLY])
    public val semantics: VersionOperations<SemanticVersion>
        get() = SemanticOperations

    /**
     * Mutable view of Stonecutter configuration flags.
     *
     * Flags are used to configure the internal behaviour of the plugin.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-controller#flags">Flags Documentation</a>
     */
    public val flags: StonecutterFlags

    /**
     * Configures Stonecutter configuration flags.
     *
     * Flags are used to configure the internal behaviour of the plugin.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-controller#flags">Flags Documentation</a>
     */
    public infix fun flags(action: Action<StonecutterFlags>): Unit =
        action.execute(flags)
}
