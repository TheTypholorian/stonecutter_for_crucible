@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import io.kotest.matchers.string.shouldContain
import runGradle
import shouldFailBuild
import tempDirFixture

val FlagTest by testSuite {
    tempDirFixture() asParameterForEach {
        integrationTest("assign operator") {
            runGradle(it).output shouldContain "[flag] receiver=minceraft"
        }

        testSuite("write scoped flags") {
            integrationTest("settings") {
                runGradle(it).output shouldContain "[flag] hard_mode=true"
            }
            integrationTest("controller") {
                runGradle(it).output shouldContain "[flag] generate_manifest=false"
            }
            integrationTest("node") {
                runGradle(it, ":1:printFlags").output shouldContain "[flag] application_task=runServer"
            }
        }

        testSuite("reject foreign scope") {
            integrationTest("settings flag in controller") {
                shouldFailBuild { runGradle(it) }.message shouldContain "cannot be assigned"
            }
            integrationTest("node flag in controller") {
                shouldFailBuild { runGradle(it) }.message shouldContain "cannot be assigned"
            }
            integrationTest("controller flag in node") {
                shouldFailBuild { runGradle(it, ":1:printFlags") }.message shouldContain "cannot be assigned"
            }
        }

        integrationTest("disable auto-apply under isolation") {
            runGradle(it).output shouldContain "[flag] apply=false"
        }

        integrationTest("inherit down hierarchy") {
            with(runGradle(it, ":1:printFlags").output) {
                shouldContain("[flag] hard_mode=true")
                shouldContain("[flag] receiver=minceraft")
            }
        }
    }
}
