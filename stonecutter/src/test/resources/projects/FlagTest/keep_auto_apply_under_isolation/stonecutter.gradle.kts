import dev.kikugie.stonecutter.controller.flag.StonecutterFlag

plugins {
    id("dev.kikugie.stonecutter")
}

sc active "1"

println("[flag] apply=" + stonecutter.flags[StonecutterFlag.AUTO_APPLY_PLUGIN])
