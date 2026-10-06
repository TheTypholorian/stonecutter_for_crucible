plugins {
    alias(common.plugins.kotlin.jvm) apply false
    alias(common.plugins.kotlin.serialization) apply false
    alias(common.plugins.kotlin.dokka)
    alias(common.plugins.gradle.dotenv)
}

version = property("version").toString()
val baseVersion: String = property("version").toString()
    .substringBefore('-')
    .let {
        val trimmed = it.substringBeforeLast('.')
        if ('.' in trimmed) trimmed else it
    }

repositories {
    mavenCentral()
}

dependencies {
    dokka(project(":stonecutter"))
    dokka(project(":stitcher"))
    dokka(project(":model"))
    dokka(project(":annotations"))

    dokkaHtmlPlugin(common.kotlin.dokka.versioning)
}

dokka {
    moduleName = "Stonecutter KDoc"

    pluginsConfiguration.html {
        homepageLink = "https://stonecutter.kikugie.dev"
        footerMessage = "(c) 2026 KikuGie"

        customAssets.from("tools/logo-icon.svg")
        customAssets.from(layout.projectDirectory.dir("tools/dokka-icons").asFileTree.matching { include("*.svg") })
        customStyleSheets.from("tools/dokka-theme.css")
    }

    pluginsConfiguration.versioning {
        version = baseVersion
        olderVersionsDir = file("kdoc-versions")
    }
}
