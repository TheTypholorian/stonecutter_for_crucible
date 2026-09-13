@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.disable
import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import runGradle
import shouldFailBuild
import tempDirFixture
import kotlin.io.path.useLines

val SwapTest by testSuite {
    tempDirFixture() asParameterForEach {
        integrationTest("simple swap") {
            runGradle(it, ":1:run").output shouldContain "Hello Tim!"
            runGradle(it, ":2:run").output shouldContain "Hello Lace!"
        }

        integrationTest("swap arguments") {
            runGradle(it, ":1:run").output shouldContain "Hello Lace!"
            runGradle(it, ":2:run").output shouldContain "Bye Lace!"
        }

        integrationTest("swap indents") {
            runGradle(it, "stonecutterSwitchTo2")
            it.resolve("src/main/java/Example.java")
                .useLines { lines -> lines.first { "Hello Lace!" in it } }
                .shouldStartWith(" ".repeat(8))
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/17
        testSuite("lookup scopes") {
            // A default `>>` scope skips the empty region, then captures up to the first whitespace.
            integrationTest("word scope default") {
                runGradle(it, ":2:run").output shouldContain "Bye Lace!"
            }

            // `>> 'str'` matches the string without including it in the scope.
            integrationTest("word scope custom") {
                runGradle(it, ":2:run").output shouldContain "Bye Tim!"
            }

            // `>>+ 'str'` matches the string and includes it in the scope.
            integrationTest("word scope capturing") {
                runGradle(it, ":2:run").output shouldContain "Bye Tim!"
            }

            // FIXME(rework): an unsatisfied swap lookup is not reported — the build succeeds and the
            //  scope is left as-is. Matches the three commented-out `string lookup …` cases in
            //  `FileAstParserTest > reported cases`; re-enable together with those.
            integrationTest("word scope unmatched", TestConfig.disable()) {
                shouldFailBuild { runGradle(it, ":2:run") }.buildResult.output shouldContain "Lookup string not found"
            }
        }
    }
}
