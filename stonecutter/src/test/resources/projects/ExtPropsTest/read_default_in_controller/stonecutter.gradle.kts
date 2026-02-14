import org.gradle.kotlin.dsl.extra

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.20.1"


check(extra.has("prop1")) { "Missing prop1" }
check(!extra.has("prop2")){ "Resolved prop2" }