import org.gradle.kotlin.dsl.extra

check(extra.has("prop1")) { "Missing prop1" }
check(!extra.has("prop2")) { "Resolved prop2" }

sc properties rootProject.file("other.properties.toml")
check(extra.has("prop2")) { "Missing prop2" }