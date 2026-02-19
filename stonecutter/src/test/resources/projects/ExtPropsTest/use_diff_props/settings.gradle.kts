import org.gradle.kotlin.dsl.extra

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter {
    create(rootProject) {
        version("1.21-fabric", "1.21")
        version("26.1-snapshot-7-fabric", "26.1-snapshot.7")
    }
}