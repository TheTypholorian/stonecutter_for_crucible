package dev.kikugie.stonecutter.settings.properties

import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import java.io.File

public interface ExtendedPropertyProvider {
    /**
     * Loads properties from a TOML [file].
     */
    @StonecutterExperimentalAPI
    public infix fun properties(file: File)
}