import dev.kikugie.stonecutter.controller.flag.StonecutterFlag

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter {
    flags {
        StonecutterFlag.HARD_MODE = true
    }
    create(rootProject) {
        versions("1", "2")
    }
}

println("[flag] hard_mode=" + stonecutter.flags[StonecutterFlag.HARD_MODE])
