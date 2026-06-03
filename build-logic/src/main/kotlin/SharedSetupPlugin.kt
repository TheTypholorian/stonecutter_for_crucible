import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.antlr.AntlrTask
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

private const val JAVA_VERSION: Int = 21
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
            }
        }

        configureAntlr(this)
    }

    private fun configureAntlr(project: Project) = with(project) {
        val dir = layout.projectDirectory.asFile.absolutePath
        val ext = extensions.create<AntlrExtension>("antlr")

        tasks.withType<KotlinCompile> {
            val type = name.removeSurrounding("compile", "Kotlin")
            val name = "generate${type}GrammarSources"
            if (name in tasks.names) dependsOn(name)
        }

        afterEvaluate {
            tasks.withType<AntlrTask> {
                val type = name.removeSurrounding("generate", "GrammarSource").lowercase().ifBlank { "main" }
                val conf = ext.sourceSets.findByName(type) ?: return@withType
                val args = buildList {
                    if (conf.longMessages.get()) this += "-long-messages"
                    this += if (conf.listener.get()) "-listener" else "-no-listener"
                    this += if (conf.visitor.get()) "-visitor" else "-no-visitor"
                    for (lib in conf.libs.get()) {
                        this += "-lib"
                        this += "$dir/src/$type/antlr/${lib.replace('.', '/')}"
                    }
                }
                arguments = arguments + args
            }
        }
    }
}
