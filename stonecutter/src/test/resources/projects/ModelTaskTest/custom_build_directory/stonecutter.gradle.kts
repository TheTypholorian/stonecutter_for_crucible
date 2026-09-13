plugins {
    id("dev.kikugie.stonecutter")
}

sc active "1"

// Relocate the build directory to prove the branch model lands under the
// project's configured buildDirectory rather than a hardcoded `build/` path.
layout.buildDirectory.set(layout.projectDirectory.dir("custom-build"))
