import org.gradle.kotlin.dsl.extra

plugins {
    id("dev.kikugie.stonecutter")
}

check(extra.has("prop1")) { "Missing prop1" }
check(!extra.has("prop2")){ "Resolved prop2" }