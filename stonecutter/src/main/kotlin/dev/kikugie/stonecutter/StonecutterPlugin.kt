package dev.kikugie.stonecutter

import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.stonecutter.settings.StonecutterSettingsExtension
import dev.kikugie.stonecutter.settings.StonecutterSettingsImpl
import dev.kikugie.stonecutter.util.createStonecutterExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.build.event.BuildEventsListenerRegistry
import javax.inject.Inject

/**
 * The Stonecutter Gradle plugin.
 *
 * Registers the `stonecutter` extension, additionally aliased as `sc`.
 *
 * The plugin is only effective on [Settings].
 * When applied to a [Project] it only serves as a marker.
 *
 * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/guides/start/project-setup">Project Setup Documentation</a>
 */
public abstract class StonecutterPlugin @Inject constructor(
    private val objects: ObjectFactory,
    private val registry: BuildEventsListenerRegistry
) : Plugin<ExtensionAware> {
    public companion object {
        /**Current Stonecutter version.*/
        public const val VERSION: String = __VERSION

        /**The minimum required Gradle version for Stonecutter to function properly.*/
        @JvmField public val GRADLE_COMPAT: SemanticVersion = SemanticVersion.parse(__GRADLE_COMPAT)
    }

    /**
     * Applies the plugin either to [Settings] or [Project].
     * Applying the plugin to an incorrect target will throw an exception.
     */
    override fun apply(target: ExtensionAware): Unit = when (target) {
        is Settings -> {
            check(SemanticVersion.parse(target.gradle.gradleVersion) >= GRADLE_COMPAT) {
                "Stonecutter requires at least Gradle $GRADLE_COMPAT. See https://gradle.org/releases/"
            }
            target.createStonecutterExtension<StonecutterSettingsExtension, StonecutterSettingsImpl>(registry)
        }
        is Project -> Unit
        else -> error("The plugin may only be applied to settings and projects")
    }
}