@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainOnlyOnce
import read
import runGradle
import shouldFailBuild
import tempDirFixture

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

        integrationTest("create extension alias") { runGradle(it) }
        integrationTest("fail with disabled alias") { shouldFailBuild { runGradle(it) } }
    }
}