package dev.kikugie.stonecutter.controller.flag

/**Determines the context a [StonecutterFlag] belongs to and can be assigned in.*/
public enum class FlagScope {
    /** Flags applicable in `settings.gradle(.kts)`. */
    SETTINGS,

    /** Flags applicable in `stonecutter.gradle(.kts)`. */
    CONTROLLER,

    /** Flags applicable in `build.gradle(.kts)`. */
    NODE,

    /** Flags applicable in any build script without inheritance. */
    LOCAL
}
