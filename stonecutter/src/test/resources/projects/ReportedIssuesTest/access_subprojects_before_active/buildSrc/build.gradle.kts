plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins.create("subprojectsAccess") {
        id = "test.subprojects-access"
        implementationClass = "test.SubprojectsAccessPlugin"
    }
}
