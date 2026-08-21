import dev.kikugie.stonecutter.controller.flag.StonecutterFlag

plugins {
    id("dev.kikugie.stonecutter")
}

sc active "1"

stonecutter flags {
    // APPLICATION_TASK is node-scoped and must not be assignable from the controller.
    StonecutterFlag.APPLICATION_TASK = "runServer"
}
