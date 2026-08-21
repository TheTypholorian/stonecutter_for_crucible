import dev.kikugie.stonecutter.controller.flag.StonecutterFlag

stonecutter flags {
    // IMPLICIT_RECEIVER is controller-scoped and must not be assignable from a node.
    StonecutterFlag.IMPLICIT_RECEIVER = "minceraft"
}

tasks.register("printFlags")
