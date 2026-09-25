import dev.kikugie.stonecutter.controller.flag.StonecutterFlag

plugins {
    id("dev.kikugie.stonecutter")
}

sc active "example"

stonecutter flags {
    StonecutterFlag.AUTO_APPLY_PLUGIN = false
}