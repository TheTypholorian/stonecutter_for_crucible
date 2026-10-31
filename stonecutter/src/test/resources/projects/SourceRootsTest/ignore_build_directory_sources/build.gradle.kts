plugins {
    java
}

layout.buildDirectory = file("output")

sourceSets.main {
    java.srcDir("output/excluded")
}
