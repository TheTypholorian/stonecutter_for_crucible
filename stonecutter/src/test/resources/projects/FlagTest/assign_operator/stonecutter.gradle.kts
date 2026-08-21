import dev.kikugie.stonecutter.controller.flag.StonecutterFlag

plugins {
    id("dev.kikugie.stonecutter")
}

sc active "1"

stonecutter flags {
    StonecutterFlag.IMPLICIT_RECEIVER = "minceraft"
}

println("[flag] receiver=" + stonecutter.flags[StonecutterFlag.IMPLICIT_RECEIVER])
