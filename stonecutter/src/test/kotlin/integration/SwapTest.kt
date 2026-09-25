@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import read
import runGradle
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

            integrationTest("word scope unmatched") {
                runGradle(it, ":2:stonecutterGenerate")

                val generated = it read "versions/2/build/generated/stonecutter/main/java/Example.java"
                generated shouldBe it.read("src/main/java/Example.java")
            }
        }
    }
}
