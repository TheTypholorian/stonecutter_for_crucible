@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stonecutter.controller.flag.StonecutterFlag
import dev.kikugie.stonecutter.util.propertyKey
import integrationTest
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainOnlyOnce
import io.kotest.matchers.string.shouldNotContain
import read
import runGradle
import runGradleShared
import shouldFailBuild
import tempDirFixture
import java.nio.file.Path
import java.util.zip.ZipFile

val ControllerExtensionTest by testSuite {
    tempDirFixture() asParameterForEach {
        integrationTest("create extension alias") { runGradle(it) }
        integrationTest("fail with disabled alias") { shouldFailBuild { runGradle(it) } }
        integrationTest("switch active with alias") {
            runGradle(it, "stonecutterSwitchTo2")
            it read "stonecutter.gradle.kts" shouldContain "sc active \"2\""
        }

        testSuite("plugin initialization") {
            integrationTest("fail without active call") { shouldFailBuild { runGradle(it) } }
            integrationTest("warn with base plugin") {
                runGradle(it).output shouldContain "Stonecutter branch root"
            }
            integrationTest("validate active parameter") { shouldFailBuild { runGradle(it) } }
            integrationTest("accept file active") { runGradle(it) }
        }

        testSuite("stonecutter flags") {
            integrationTest("not apply to build") {
                shouldFailBuild { runGradle(it) }.message shouldContain "Unresolved reference 'stonecutter'"
            }
            integrationTest("disable after init") {
                shouldFailBuild { runGradle(it) }.message shouldContain "Unresolved reference 'stonecutter'"
            }
            integrationTest("override default receiver") { runGradle(it, "stonecutterGenerate") }
            integrationTest("generate manifest") {
                fun getJar() = it.resolve("versions/example/build/libs/example.jar")
                    .apply(Path::shouldExist).toFile().let(::ZipFile)

                runGradle(it, "jar")
                getJar().use { jar ->
                    val meta = jar.getEntry("META-INF/MANIFEST.MF").shouldNotBeNull()
                    val contents = jar.getInputStream(meta).reader().readText()

                    contents shouldContain "Stonecutter-Plugin-Version"
                }

                runGradle(it, "jar", "--rerun-tasks", "-P${StonecutterFlag.GENERATE_MANIFEST.propertyKey}=false")
                getJar().use { jar ->
                    val meta = jar.getEntry("META-INF/MANIFEST.MF") ?: return@use
                    val contents = jar.getInputStream(meta).reader().readText()

                    contents shouldNotContain "Stonecutter-Plugin-Version"
                }
            }
        }

        testSuite("task operations") {
            integrationTest("depend on uninitialized tasks") {
                with(runGradleShared(it, "sayHelloAndGoodbye").output) {
                    shouldContain("Hello!")
                    shouldContainOnlyOnce("Goodbye!")
                    lastIndexOf("Hello") shouldBeLessThan indexOf("Goodbye!")
                }
            }

            integrationTest("generate switch configurations") {
                runGradle(it, "stonecutterIdea")
                it.resolve(".idea/runConfigurations/Stonecutter_example_switchTo1.xml").shouldExist()
                it.resolve(".idea/runConfigurations/Stonecutter_example_switchTo2.xml").shouldExist()
            }

            integrationTest("depend on task names") {
                with(runGradle(it, "sayHelloAndGoodbye").output) {
                    shouldContain("Hello!")
                    shouldContainOnlyOnce("Goodbye!")
                    lastIndexOf("Hello") shouldBeLessThan indexOf("Goodbye!")
                }
            }
        }

        testSuite("file handlers") {
            integrationTest("inherit custom handler") {
                runGradle(it, "stonecutterSwitchTo2")
                it read "src/main/resources/example.custom" shouldContain "#comment"
            }

            integrationTest("override existing handler") {
                runGradle(it, "stonecutterSwitchTo2")
                it read "src/main/java/Example.java" shouldContain "//int one = 1;"
            }

            integrationTest("throw on invalid handler") { shouldFailBuild { runGradle(it) } }
        }
    }
}
