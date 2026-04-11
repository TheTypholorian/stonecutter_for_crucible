import org.gradle.kotlin.dsl.extra

plugins {
    id("dev.kikugie.stonecutter")
}

check(extra["option"] == "0") { "${extra["option"]} != 0" }

sc.properties.tags("tag2")
check(extra["option"] == "2") { "${extra["option"]} != 2 #1" }

sc.properties.tags("tag1")
check(extra["option"] == "2") { "${extra["option"]} != 2 #2" }
