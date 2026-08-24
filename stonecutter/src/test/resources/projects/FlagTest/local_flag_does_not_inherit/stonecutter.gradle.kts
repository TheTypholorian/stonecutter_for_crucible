import dev.kikugie.stonecutter.controller.flag.StonecutterFlag

plugins {
    id("dev.kikugie.stonecutter")
}

sc active "1"

// LOCAL-scoped flag assigned in the controller; it must not reach the nodes.
stonecutter flags {
    StonecutterFlag.EXPOSE_PROPERTIES = true
}
