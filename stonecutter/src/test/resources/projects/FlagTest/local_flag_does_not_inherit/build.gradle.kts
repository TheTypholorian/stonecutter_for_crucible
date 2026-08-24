import dev.kikugie.stonecutter.controller.flag.StonecutterFlag

tasks.register("printFlags") {
    val expose = stonecutter.flags[StonecutterFlag.EXPOSE_PROPERTIES]
    doLast {
        println("[flag] expose=$expose")
    }
}
