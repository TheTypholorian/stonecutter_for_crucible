package dev.kikugie.stonecutter.settings.properties

import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import java.io.File

public interface ExtendedPropertyProvider {
    /**
     * Loads properties from a TOML file at the given [path].
     *
     * The path is relative to the **project directory**, not the build script location:
     * - `settings.gradle(.kts)`: same as the directory of the file
     * - `stonecutter.gradle(.kts)`: same as the directory of the file
     * - `build.gradle(.kts)`: located in `versions/{project.name}`
     */
    @StonecutterExperimentalAPI
    public infix fun properties(path: String)
}