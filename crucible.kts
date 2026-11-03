modules.register("stonecutter.main") {
    sourceInputFolder.set(projectRoot.resolve("stonecutter", "src", "main"))

    repositories {
        mavenCentral()
        typhoNet()
    }

    dependencies {
        kotlin("stdlib")
    }

    resources()
    kotlin()

    compileTask()
    jarTask()
}