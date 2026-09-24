package dev.kikugie.stonecutter.controller.flag

import dev.kikugie.stonecutter.model.process.ConfigurationFlag
import org.gradle.api.SupportsKotlinAssignmentOverloading

/**
 * A primitive configuration option for Stonecutter.
 *
 * Flags can be assigned via [stonecutter.flags { }][dev.kikugie.stonecutter.controller.StonecutterControllerExtension.flags],
 * or with a `dev.kikugie.stonecutter.`[key] property.
 *
 * @property key The flag key, used in [named] and Gradle property accessors
 * @property default The default value of the flag
 * @property scope The [FlagScope] this flag belongs to
 * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-controller#flags">Flags Documentation</a>
 */
@SupportsKotlinAssignmentOverloading
@ConsistentCopyVisibility
public data class StonecutterFlag<T : Any> private constructor(
    public val key: String,
    public val default: T,
    public val scope: FlagScope = FlagScope.CONTROLLER,
) {
    init {
        require(key.isNotBlank()) { "Flag key cannot be blank" }
        require(key.all { it in 'a'..'z' || it == '_' }) { "Flag key must be written in snake case" }
        require(default.isPrimitive()) { "Flag value must be a primitive type, got ${default::class.simpleName} instead" }
        check(REGISTRY.putIfAbsent(key, this) == null) { "Flag '$key' is already registered" }
    }

    /**The [ConfigurationFlag] used as the key when this flag is stored in a [ConfigurationRegistry][dev.kikugie.stonecutter.model.process.ConfigurationRegistry].*/
    @Suppress("UNCHECKED_CAST")
    internal val configuration: ConfigurationFlag<T> = when (val value = default) {
        is Boolean -> ConfigurationFlag(key, value)
        is Int -> ConfigurationFlag(key, value)
        is Long -> ConfigurationFlag(key, value)
        is Float -> ConfigurationFlag(key, value)
        is Double -> ConfigurationFlag(key, value)
        is String -> ConfigurationFlag(key, value)
        else -> error("Flag value must be a primitive type, got ${value::class.simpleName} instead")
    } as ConfigurationFlag<T>

    /**Converts the provided [value] to the flag's type.*/
    @Suppress("UNCHECKED_CAST")
    public fun fromString(value: String): T = when (default) {
        is String -> value
        is Boolean -> value.toBoolean()
        is Int -> value.toInt()
        is Long -> value.toLong()
        is Float -> value.toFloat()
        is Double -> value.toDouble()
        else -> UnsupportedOperationException("Unreachable")
    } as T

    public companion object {
        private val REGISTRY: MutableMap<String, StonecutterFlag<*>> = mutableMapOf()
        private fun Any.isPrimitive(): Boolean = when (this) {
            is Boolean, is Int, is Long, is Float, is Double, is String -> true
            else -> false
        }

        /**Returns a flag by its [key][StonecutterFlag.key].*/
        @JvmStatic
        public fun named(name: String): StonecutterFlag<*> =
            checkNotNull(REGISTRY[name]) { "Flag '$name' is not registered" }

        /**
         * Returns all registered flags belonging to the given [scope].
         * [FlagScope.LOCAL] flags are included in every scope.
         */
        internal fun registered(scope: FlagScope): List<StonecutterFlag<*>> =
            REGISTRY.values.filter { it.scope == scope || it.scope == FlagScope.LOCAL }

        /**
         * Configures the automatic plugin application behaviour.
         * When disabled, the Stonecutter plugin will not be automatically applied
         * to `stonecutter.gradle(.kts)` and versioned `build.gradle(.kts)` files,
         * and will have to be added explicitly with
         * ```kotlin
         * plugins {
         *     id("dev.kikugie.stonecutter")
         * }
         * ```
         *
         * **Default**: `true`
         */
        @JvmField
        public val AUTO_APPLY_PLUGIN: StonecutterFlag<Boolean> = StonecutterFlag("auto_apply_plugin", true)

        /**
         * Configures versioned source generation on IntelliJ sync.
         * When disabled, sourced will only be updated when the project is built.
         *
         * **Default**: `true`
         */
        @JvmField
        public val GENERATE_SOURCES_ON_SYNC: StonecutterFlag<Boolean> = StonecutterFlag("generate_sources_on_sync", true)

        /**
         * Configures run configuration generation in IntelliJ.
         * When enabled, version switch tasks will be available per project in the
         * "Run configurations" dropdown menu.
         *
         * **Default**: `true`
         */
        @JvmField
        public val GENERATE_SWITCH_ACTIONS: StonecutterFlag<Boolean> = StonecutterFlag("generate_switch_actions", true)

        /**
         * Configures the source generation mode for [dev.kikugie.stonecutter.build.StonecutterBuildExtension].
         * Normally, the build plugin won't detect source directories added after the source set creation.
         * With this flag enabled, it will do a second pass after project evaluation.
         * Disable if it causes issues and append source sets manually with the help of [dev.kikugie.stonecutter.build.task.StonecutterBuildTasks].
         *
         * **Default**: `true`
         */
        @JvmField
        public val APPEND_SOURCES_AFTER_EVAL: StonecutterFlag<Boolean> = StonecutterFlag("extra_source_check", true)

        /**
         * Saves the Stonecutter parameters to project's `build/stonecutter-cache` directories
         * to be used by the IntelliJ plugin and other third-party tools.
         *
         * **Default**: `true`
         */
        @JvmField
        public val SERIALIZE_TREE_MODEL: StonecutterFlag<Boolean> = StonecutterFlag("serialize_tree_model", true)

        /**
         * Configures the implicit receiver target used in file processing.
         *
         * **Default**: `"minecraft"`
         * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#targets">Targets Documentation</a>
         */
        @JvmField
        public val IMPLICIT_RECEIVER: StonecutterFlag<String> = StonecutterFlag("implicit_receiver", "minecraft")

        /**
         * Appends Stonecutter metadata to [Jar][org.gradle.jvm.tasks.Jar] manifests.
         *
         * The following entries are added when the flag is enabled:
         * - `Stonecutter-Plugin-Version`: [StonecutterPlugin.VERSION][dev.kikugie.stonecutter.StonecutterPlugin.VERSION]
         * - `Stonecutter-Gradle-Version`: [CradleVersion.current()][org.gradle.util.GradleVersion.current]
         * - `Stonecutter-Node-Project`: [sc.current.project][dev.kikugie.stonecutter.build.StonecutterBuildExtension.current]
         * - `Stonecutter-Node-Version`: [sc.current.version][dev.kikugie.stonecutter.build.StonecutterBuildExtension.current]
         *
         * **Default**: `true`
         */
        @JvmField
        public val GENERATE_MANIFEST: StonecutterFlag<Boolean> = StonecutterFlag("generate_manifest", true)

        /**
         * Configures the line separator used when writing processed files.
         *
         * **Default**: [System.lineSeparator]
         */
        @JvmField
        public val LINE_SEPARATOR: StonecutterFlag<String> =
            StonecutterFlag("line_separator", System.lineSeparator())

//        /**
//         * Configures the task IntelliJ plugin should mark as the primary runnable action.
//         * (i.e. that starts the application or launches the game)
//         *
//         * **Default**: `"runClient"`
//         */
//        @JvmField
//        public val APPLICATION_TASK: StonecutterFlag<String> =
//            StonecutterFlag("application_task", "runClient", FlagScope.NODE)

        /**
         * Opts the project into Groovy build scripts.
         *
         * When enabled, generated controller and versioned scripts are named `.gradle`
         * rather than `.gradle.kts` even where no `.gradle` file exists yet, and the
         * *'Limited Groovy DSL support for Stonecutter'* message is no longer printed.
         *
         * **Default**: `false`
         * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/faq#groovy-support">Groovy Support FAQ</a>
         */
        @JvmField
        public val HARD_MODE: StonecutterFlag<Boolean> =
            StonecutterFlag("hard_mode", false, FlagScope.SETTINGS)

        /**
         * Reports a structured property key that is written more than once.
         *
         * The report is always made - this only raises it from `debug` to `warn`, since a
         * duplicate is expected when tagged files deliberately override one another.
         *
         * **Default**: `false`
         */
        @JvmField
        public val WARN_DUPLICATE_PROPERTIES: StonecutterFlag<Boolean> =
            StonecutterFlag("warn_duplicate_properties", false, FlagScope.LOCAL)

        /**
         * Registers the `sc` alias alongside the `stonecutter` extension.
         *
         * The alias is added when the extension is created, before any `flags { }` block runs,
         * so this flag is only read from the `dev.kikugie.stonecutter.short_extension`
         * structured or Gradle property.
         *
         * **Default**: `true`
         */
        @JvmField
        public val SHORT_EXTENSION: StonecutterFlag<Boolean> =
            StonecutterFlag("short_extension", true, FlagScope.LOCAL)

        /**
         * Exposes resolved structured properties as Gradle
         * [extra][org.gradle.api.plugins.ExtraPropertiesExtension] entries.
         *
         * When enabled, every property retrievable from
         * [stonecutter.properties.get][dev.kikugie.stonecutter.data.property.StructuredPropertiesExtension.get]
         * is written to the current scope's [extra][org.gradle.api.plugins.ExtraPropertiesExtension]
         * and will be available with [project.property][org.gradle.api.Project.property].
         *
         * **Default**: `true`
         */
        @JvmField
        public val EXPOSE_PROPERTIES: StonecutterFlag<Boolean> =
            StonecutterFlag("expose_properties", true, FlagScope.LOCAL)
    }
}
