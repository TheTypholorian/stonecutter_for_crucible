import dev.kikugie.stonecutter.controller.flag.StonecutterFlag

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1"

stonecutter flags {
    StonecutterFlag.LINE_SEPARATOR = "\r\n"
}
