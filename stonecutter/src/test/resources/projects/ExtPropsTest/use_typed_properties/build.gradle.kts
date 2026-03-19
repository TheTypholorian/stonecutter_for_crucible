import dev.kikugie.semver.data.SemanticVersion

val (mc, loader) = sc.current.project.split('-', limit = 2)
assert(mc == "1.17.1") { "MC is not 1.17.1" }
assert(loader == "forge") { "Loader is not forge" }

sc.properties.tags(mc, loader)

val nativeForgeStr = property("deps.forge")
assert(nativeForgeStr == "37.1.1") { "Native forge is not 37.1.1" }

val typedForgeStr: String = sc.properties["deps.forge"]
assert(typedForgeStr == "37.1.1") { "Typed forge is not 37.1.1" }

val typedForgeSemver: SemanticVersion = sc.properties["deps.forge"]
assert(typedForgeSemver == SemanticVersion(37, 1, 1)) { "SemVer forge is not 37.1.1" }

val forgeJsonElement = sc.properties.raw("deps", "forge")
assert(forgeJsonElement.toString() == "\"37.1.1\"")