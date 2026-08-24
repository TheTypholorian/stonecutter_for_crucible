import org.gradle.kotlin.dsl.extra

// Force the extension to be created so the initial export runs.
stonecutter.properties

check(extra.has("greeting")) { "`greeting` should be exported" }
check(extra["greeting"] == "hello") { "`greeting` should be `hello`, got ${extra.get("greeting")}" }
check(extra.has("fabric.loader")) { "`fabric.loader` should be exported before tagging" }
check(!extra.has("loader")) { "`loader` should not be retrievable before tagging" }

stonecutter properties {
    tags("fabric")
}

check(extra["loader"] == "fabric") { "`loader` should resolve to `fabric` after tagging, got ${extra.get("loader")}" }
// Gradle's `extra` has no removal API, so unassigned keys become `null` rather than absent.
check(extra.get("fabric.loader") == null) { "`fabric.loader` should be unassigned after tagging" }
