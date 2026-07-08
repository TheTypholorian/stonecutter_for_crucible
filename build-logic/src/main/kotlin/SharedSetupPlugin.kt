import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

private const val JAVA_VERSION: Int = 25
private const val KOTLIN_VERSION: String = "2.4"

@Suppress("unused")
open class SharedSetupPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        repositories {
            mavenCentral()
            maven("https://maven.kikugie.dev/releases")
            maven("https://central.sonatype.com/repository/maven-snapshots/")
        }

        tasks {
            withType<Test> {
                useJUnitPlatform()
            }
        }

        extensions.configure<KotlinJvmProjectExtension>("kotlin") {
            jvmToolchain {
                languageVersion = JavaLanguageVersion.of(JAVA_VERSION)
                vendor = JvmVendorSpec.ADOPTIUM
            }

            compilerOptions {
                val version = KotlinVersion.fromVersion(KOTLIN_VERSION)
                languageVersion = version
                apiVersion = version

                compilerOptions {
                    freeCompilerArgs.add("-Xcontext-parameters")
                    freeCompilerArgs.add("-Xexplicit-backing-fields")
                }
            }
        }
    }
}
