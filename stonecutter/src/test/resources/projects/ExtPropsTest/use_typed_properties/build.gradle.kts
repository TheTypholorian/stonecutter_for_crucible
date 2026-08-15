import dev.kikugie.semver.data.SemanticVersion

val (mc, loader) = sc.current.project.split('-', limit = 2)
assert(mc == "1.17.1") { "MC is not 1.17.1" }
assert(loader == "forge_loader") { "Loader is not forge" }

sc.properties.tags(mc, loader)

val nativeForgeStr = property("deps.forge_loader")
assert(nativeForgeStr == "37.1.1") { "Native forge is not 37.1.1" }

val typedForgeStr: String = sc.properties.getAs("deps.forge_loader")
assert(typedForgeStr == "37.1.1") { "Typed forge is not 37.1.1" }

val typedForgeSemver: SemanticVersion = sc.properties.getAs("deps.forge_loader")
assert(typedForgeSemver == SemanticVersion(37, 1, 1)) { "SemVer forge is not 37.1.1" }

val forgeJsonElement = sc.properties.raw("deps.forge_loader")
assert(forgeJsonElement.toString() == "\"37.1.1\"")