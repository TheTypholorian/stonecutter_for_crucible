import dev.kikugie.stonecutter.controller.flag.StonecutterFlag

tasks.register("printFlags") {
    val hard = stonecutter.flags[StonecutterFlag.HARD_MODE]
    val receiver = stonecutter.flags[StonecutterFlag.IMPLICIT_RECEIVER]
    doLast {
        println("[flag] hard_mode=$hard")
        println("[flag] receiver=$receiver")
    }
}
