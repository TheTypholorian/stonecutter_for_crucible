plugins {
    java
}

layout.buildDirectory = file("output")

sourceSets.main {
    java.srcDir("output/extra")
    java.srcDir("../../external")
}

stonecutter {
    files {
        disable(true, "Disabled.java", "Generated.java", "External.java")
    }
}
