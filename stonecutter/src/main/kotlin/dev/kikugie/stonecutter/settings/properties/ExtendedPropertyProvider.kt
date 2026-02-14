package dev.kikugie.stonecutter.settings.properties

import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import java.io.File

public interface ExtendedPropertyProvider {
    @StonecutterExperimentalAPI
    public infix fun properties(path: String)
}