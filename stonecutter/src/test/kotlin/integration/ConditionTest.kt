@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import read
import runGradle
import shouldFailBuild
import tempDirFixture

val ConditionTest by testSuite {
    tempDirFixture() asParameterForEach {
        integrationTest("simple version") {
            runGradle(it, ":1:run").output shouldContain "Hello world!"
            runGradle(it, ":2:run").output shouldNotContain "Hello world!"
        }

        integrationTest("simple constant") {
            runGradle(it, ":1:run").output shouldContain "Hello world!"
            runGradle(it, ":2:run").output shouldNotContain "Hello world!"
        }

        integrationTest("simple dependency") {
            runGradle(it, ":1:run").output shouldContain "Hello world!"
            runGradle(it, ":2:run").output shouldNotContain "Hello world!"
        }

        // A `-*` bound reaches down into the pre-releases of its own release line, which a bare
        // bound of the same version excludes. The active version - the first one listed - compiles
        // the shared source as it stands, so the committed file has to match it and the pre-release
        // comes second, where the processor actually runs.
        integrationTest("pre-release wildcard") { dir ->
            runGradle(dir, ":1.21.1:run").output.run {
                this shouldContain "WILDCARD"
                this shouldContain "BARE"
            }
            runGradle(dir, ":1.21-alpha.1:run").output.run {
                this shouldContain "WILDCARD"
                this shouldNotContain "BARE"
            }
        }

        integrationTest("if-else chain") {
            for (i in 1..4) runGradle(it, ":$i:run").output shouldContain "$i!"
        }

        integrationTest("nested conditions") {
            runGradle(it, "stonecutterSwitchTo1.20.1")
            runGradle(it, ":1.20.1:run").output shouldContain "CASE B"
        }

        // A branch after `else` can never be reached, but it is a warning rather than an error -
        // the file still processes and the unreachable branch stays commented.
        integrationTest("duplicate else") {
            runGradle(it, ":1:run").output shouldContain "1!"
        }

        integrationTest("unclosed scope") {
            shouldFailBuild { runGradle(it, "run") }.buildResult.output shouldContain "Example.java:3:9"
        }

        // Placeholders are deliberately flat at every depth - the `/^¹ ¹^/` superscripts were dropped
        // for backwards compatibility, so both versions render the same `/^nested^/`.
        integrationTest("included comments") {
            runGradle(it, "stonecutterSwitchTo2")
            it read "src/main/java/Example.java" shouldContain "/^nested^/"

            runGradle(it, "stonecutterSwitchTo1")
            it read "src/main/java/Example.java" shouldContain "/^nested^/"
        }
    }
}
