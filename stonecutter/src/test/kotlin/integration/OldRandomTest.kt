@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.disable
import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import read
import runGradle
import shouldFailBuild
import tempDirFixture
import kotlin.io.path.useLines

@Deprecated("Should be migrated to dedicated tests")
val OldRandomTest by testSuite {
    tempDirFixture() asParameterForEach {
        integrationTest("simple version") {
            runGradle(it, ":1:run").output shouldContain "Hello world!"
            runGradle(it, ":2:run").output shouldNotContain "Hello world!"
        }

        integrationTest("unclosed scope") {
            shouldFailBuild { runGradle(it, "run") }.buildResult.output shouldContain "Example.java:3:9"
        }

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

        integrationTest("simple constant") {
            runGradle(it, ":1:run").output shouldContain "Hello world!"
            runGradle(it, ":2:run").output shouldNotContain "Hello world!"
        }

        integrationTest("simple dependency") {
            runGradle(it, ":1:run").output shouldContain "Hello world!"
            runGradle(it, ":2:run").output shouldNotContain "Hello world!"
        }

        integrationTest("if-else chain") {
            for (i in 1..4) runGradle(it, ":$i:run").output shouldContain "$i!"
        }

        // FIXME(rework): nested comments now use flat `/^ ^/` placeholders — the depth superscript (`/^¹ ¹^/`) is gone.
        integrationTest("included comments", TestConfig.disable()) {
            runGradle(it, "stonecutterSwitchTo2")
            it read "src/main/java/Example.java" shouldContain "/^¹nested¹^/"

            runGradle(it, "stonecutterSwitchTo1")
            it read "src/main/java/Example.java" shouldContain "/^nested^/"
        }

        integrationTest("nested conditions") {
            runGradle(it, "stonecutterSwitchTo1.20.1")
            runGradle(it, ":1.20.1:run").output shouldContain "CASE B"
        }

        /**
         * Default `$ swap >>` scopes should first skip the empty region,
         * and then capture everything until the first whitespace.
         * @see <a href="https://codeberg.org/stonecutter/stonecutter/issues/17">#17</a>
         */
        integrationTest("word scope default") {
            runGradle(it, ":2:run").output shouldContain "Bye Lace!"
        }

        /**
         * Custom matchers `$ swap >> 'str'` should match the string,
         * without including it in the scope.
         * @see <a href="https://codeberg.org/stonecutter/stonecutter/issues/17">#17</a>
         */
        integrationTest("word scope custom") {
            runGradle(it, ":2:run").output shouldContain "Bye Tim!"
        }

        /**
         * Custom matchers `$ swap >>+ 'str'` should match the string,
         * **including** it in the scope.
         * @see <a href="https://codeberg.org/stonecutter/stonecutter/issues/17">#17</a>
         */
        integrationTest("word scope capturing") {
            runGradle(it, ":2:run").output shouldContain "Bye Tim!"
        }

        /*
        /**
         * Custom matchers that couldn't be satisfied until
         * the scope was closed should report an error.
         * @see <a href="https://codeberg.org/stonecutter/stonecutter/issues/17">#17</a>
         */
        integrationTest("word scope unmatched") {
            shouldFailBuild { runGradle(it, ":2:run") }.buildResult.output shouldContain "Lookup string not found"
        }
        */
    }
}
