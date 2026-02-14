import org.gradle.kotlin.dsl.extra

check(extra.has("prop1")) { "Missing prop1" }
check(extra.has("prop2")) { "Missing prop2" }