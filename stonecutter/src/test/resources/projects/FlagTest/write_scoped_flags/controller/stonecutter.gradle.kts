import dev.kikugie.stonecutter.controller.flag.StonecutterFlag

plugins {
    id("dev.kikugie.stonecutter")
}

sc active "1"

stonecutter flags {
    StonecutterFlag.GENERATE_MANIFEST = false
}

println("[flag] generate_manifest=" + stonecutter.flags[StonecutterFlag.GENERATE_MANIFEST])
