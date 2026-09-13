@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import io.kotest.matchers.string.shouldContain
import runGradle
import tempDirFixture

val GroovyCompatibilityTest by testSuite {
    tempDirFixture() asParameterForEach {
        testSuite("settings") {
            integrationTest("accept combined setup") { runGradle(it, "stonecutterGenerate") }
            integrationTest("use implicit shared") { runGradle(it, "stonecutterGenerate") }
            integrationTest("use explicit shared") { runGradle(it, "stonecutterGenerate") }
            integrationTest("declare branch versions") { runGradle(it, ":subproject:3:stonecutterGenerate") }
        }

        testSuite("controller") {
            integrationTest("use parameters function") {
                with(runGradle(it).output) {
                    shouldContain("Parameters for :1")
                    shouldContain("Parameters for :2")
                }
            }
        }

        testSuite("build") {
            integrationTest("access parsed wrapper") { runGradle(it) }
            integrationTest("compare parsed wrapper") {
                runGradle(it).output shouldContain "[1: 0 true true 1]"
            }
        }
    }
}
