import dev.kikugie.stonecutter.controller.flag.StonecutterFlag

stonecutter flags {
    StonecutterFlag.APPLICATION_TASK = "runServer"
}

tasks.register("printFlags") {
    val app = stonecutter.flags[StonecutterFlag.APPLICATION_TASK]
    doLast { println("[flag] application_task=$app") }
}
