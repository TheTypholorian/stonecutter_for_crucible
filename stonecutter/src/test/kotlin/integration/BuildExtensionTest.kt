@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainOnlyOnce
import read
import runGradle
import runGradleIncremental
import shouldFailBuild
import tempDirFixture
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText

val BuildExtensionTest by testSuite {
    tempDirFixture() asParameterForEach {
        testSuite("process parameters") {
            integrationTest("allow regular values") { runGradle(it) }
            integrationTest("validate constant names") { shouldFailBuild { runGradle(it) } }
            integrationTest("validate swap names") { shouldFailBuild { runGradle(it) } }
            integrationTest("validate dependency names") { shouldFailBuild { runGradle(it) } }

            integrationTest("allow shared replacement ids") { runGradle(it) }
            integrationTest("validate replacement ids") { shouldFailBuild { runGradle(it) } }
            integrationTest("require replacement direction") { shouldFailBuild { runGradle(it) } }

            integrationTest("register multiple replacements") {
                runGradle(it, "stonecutterSwitchTo2")
                with(it read "src/main/java/Example.java") {
                    shouldContainOnlyOnce("a.d.c")
                    shouldContainOnlyOnce("1.3.4")
                }
            }
        }

        testSuite("file processing") {
            integrationTest("use project version") {
                runGradle(it, "stonecutterSwitchTo1.0-example")
                it read "src/main/java/Example.java" shouldBe """
                    //? if <1.0
                    //class Example {}
                """.trimIndent()
            }
        }

        // `process()` is called on a file outside the generated sources, so it has to resolve
        // the source set itself: the replacement scoped to `java/**` must not touch a resource.
        integrationTest("filter processed file replacements") {
            runGradle(it)
            it read "versions/1/build/processed.txt" shouldBe """
                scoped = LEGACY_SCOPED
                shared = MODERN_SHARED
            """.trimIndent() + "\n"
        }

        integrationTest("exclude filtered replacements outside source roots") {
            runGradle(it)
            it read "versions/1/build/processed.txt" shouldBe """
                scoped = LEGACY_SCOPED
                shared = MODERN_SHARED
            """.trimIndent() + "\n"
        }

        integrationTest("apply line separator to processed file") {
            runGradle(it)
            it read "versions/1/build/processed.txt" shouldBe "first\r\nsecond\r\n"
        }

        integrationTest("keep line separator of processed file") {
            it.resolve("config/data.txt").createParentDirectories().writeText("first\r\nsecond\n")
            runGradle(it)
            it read "versions/1/build/processed.txt" shouldBe "first\r\nsecond\r\n"
        }

        integrationTest("pass node flags to handlers") {
            runGradle(it)
            it read "versions/1/build/processed.cfg" shouldBe "#\$ entry {\nwarn=true\n#\$}\n"
        }

        integrationTest("track processed file for configuration cache") {
            runGradleIncremental(it)
            runGradleIncremental(it).output shouldContain "Reusing configuration cache"

            it.resolve("versions/1/build/processed.txt").deleteExisting()
            runGradleIncremental(it)
            it read "versions/1/build/processed.txt" shouldBe "name = MODERN_NAME\n"
        }

        integrationTest("create extension alias") { runGradle(it) }
        integrationTest("fail with disabled alias") { shouldFailBuild { runGradle(it) } }
    }
}