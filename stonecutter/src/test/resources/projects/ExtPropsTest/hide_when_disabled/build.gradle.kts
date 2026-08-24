import org.gradle.kotlin.dsl.extra

// Force the extension to be created; with the flag turned off nothing is exported.
stonecutter.properties

check(!extra.has("greeting")) { "`greeting` should not be exported when the flag is disabled" }
