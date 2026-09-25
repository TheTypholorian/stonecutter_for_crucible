import dev.kikugie.stonecutter.settings.StonecutterSettingsExtension

buildscript {
    dependencies {
        classpath(files(providers.gradleProperty("pluginClasspath").get().split(File.pathSeparator)))
    }
}

apply(plugin = "dev.kikugie.stonecutter")

configure<StonecutterSettingsExtension> {
    create(rootProject) {
        versions("1", "2")
    }
}
