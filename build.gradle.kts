plugins {
    alias(common.plugins.kotlin.jvm) apply false
    alias(common.plugins.kotlin.serialization) apply false
    alias(common.plugins.kotlin.dokka)
}

version = property("version").toString()
val baseVersion: String = property("version").toString()
    .substringBefore('-').substringBeforeLast('.')

repositories {
    mavenCentral()
}

dependencies {
    dokka(project(":stonecutter"))
    dokka(project(":stitcher"))

    dokkaHtmlPlugin(common.kotlin.dokka.versioning)
}

dokka {
    moduleName = "Stonecutter KDoc"

    pluginsConfiguration.html {
        homepageLink = "https://stonecutter.kikugie.dev"
        footerMessage = "(c) 2026 KikuGie"

        customAssets.from("tools/logo-icon.svg")
    }

    pluginsConfiguration.versioning {
        version = baseVersion
        olderVersionsDir = file("kdoc-versions")
    }
}
