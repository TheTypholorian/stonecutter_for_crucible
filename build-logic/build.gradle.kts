plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    fun plugin(id: String, version: String): String =
        "$id:$id.gradle.plugin:$version"
    fun plugin(provider: Provider<PluginDependency>): Provider<String> =
        provider.map { plugin(it.pluginId, it.version.toString()) }

    implementation(plugin(common.plugins.kotlin.jvm))
    implementation(plugin(common.plugins.gradle.dotenv))
}

gradlePlugin {
    plugins.register("sharedSetup") {
        id = "shared-setup"
        implementationClass = "SharedSetupPlugin"
    }
}
