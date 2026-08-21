import dev.kikugie.stonecutter.controller.flag.StonecutterFlag

plugins {
    id("dev.kikugie.stonecutter")
}

sc active "1"

stonecutter flags {
    // HARD_MODE is settings-scoped and must not be assignable from the controller.
    StonecutterFlag.HARD_MODE = true
}
