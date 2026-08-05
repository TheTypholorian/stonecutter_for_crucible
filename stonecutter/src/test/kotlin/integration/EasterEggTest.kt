@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import runGradle
import shouldFailBuild
import tempDirFixture

val EasterEggTest by testSuite {
    tempDirFixture() asParameterForEach {
        // Test for "Roses are red; Violets are blue; <error> on line <10n + 2>"
        integrationTest("appreciate poetry") {
            with(shouldFailBuild { runGradle(it, "stonecutterSwitchTo2") }.buildResult.output) {
                shouldNotContain("on line 2\n")
                shouldNotContain("on line 12\n")
                shouldContain("on line 22\n")
                shouldContain("Roses are red")
                shouldContain("Violets are blue")
            }
        }
    }
}
