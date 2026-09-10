import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

private const val JAVA_VERSION: Int = 25
private const val KOTLIN_VERSION: String = "2.4"

open class SharedSetupPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        repositories {
            mavenCentral()
            maven("https://maven.kikugie.dev/releases")
            maven("https://central.sonatype.com/repository/maven-snapshots/")
        }

        tasks {
            withType<Test> {
                outputs.doNotCacheIf("test results should not be cached") { true }
            }
        }

        extensions.configure<JavaPluginExtension>("java") {
            val javaVersion = JavaVersion.toVersion(JAVA_VERSION)
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion

            toolchain {
                languageVersion = JavaLanguageVersion.of(JAVA_VERSION)
                vendor = JvmVendorSpec.ADOPTIUM
            }
        }

        extensions.configure<KotlinJvmProjectExtension>("kotlin") {
            jvmToolchain(JAVA_VERSION)

            compilerOptions {
                val version = KotlinVersion.fromVersion(KOTLIN_VERSION)
                languageVersion = version
                apiVersion = version
            }
        }
    }
}
