@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import io.kotest.matchers.paths.shouldContainFile
import io.kotest.matchers.paths.shouldNotContainFile
import io.kotest.matchers.should
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import read
import runGradle
import shouldFailBuild
import tempDirFixture
import kotlin.io.path.exists

val SettingsExtensionTest by testSuite {
    tempDirFixture() asParameterForEach {
        integrationTest("create extension alias") { runGradle(it) }
        integrationTest("fail with disabled alias") { shouldFailBuild { runGradle(it) } }

        testSuite("script resolution") {
            integrationTest("create subproject directories") {
                runGradle(it)
                it.resolve("versions") should { path -> path.exists() }
            }

            integrationTest("create subproject root") {
                runGradle(it)
                it.resolve("example") shouldContainFile "stonecutter.gradle.kts"
            }

            integrationTest("generate kts scripts by default") {
                runGradle(it, ":1:hello").output shouldContain "1!"
                it shouldContainFile "stonecutter.gradle.kts"
            }

            integrationTest("explicitly use groovy scripts") {
                runGradle(it, ":1:hello").output shouldContain "1!"
                it shouldContainFile "stonecutter.gradle"
            }

            integrationTest("recognize existing groovy scripts") {
                runGradle(it)
                it shouldNotContainFile "stonecutter.gradle.kts"
            }

            integrationTest("print groovy compatibility warning") {
                runGradle(it).output shouldContain "Warning:"
            }

            integrationTest("mute compat warning with property") {
                runGradle(it).output shouldNotContain "Warning:"
            }
        }

        testSuite("tree builder") {
            integrationTest("use existing shared action") {
                runGradle(it)
                it.resolve("versions") should { path -> path.exists() }
            }

            integrationTest("apply shared explicitly") {
                runGradle(it)
                it.resolve("versions") should { path -> path.exists() }
            }

            integrationTest("not allow inherit call in root") {
                shouldFailBuild { runGradle(it) }
            }

            integrationTest("not allow inherit call in default branch") {
                shouldFailBuild { runGradle(it) }
            }

            integrationTest("implicitly assign vcs version") {
                runGradle(it)
                it read "stonecutter.gradle.kts" shouldContain "\"3\""
            }

            integrationTest("overwrite vcs version") {
                runGradle(it)
                it read "stonecutter.gradle.kts" shouldContain "\"1\""
            }

            integrationTest("validate branch names") { shouldFailBuild { runGradle(it) } }
            integrationTest("validate version consistency") { shouldFailBuild { runGradle(it) } }
            integrationTest("have no vers function") { shouldFailBuild { runGradle(it) } }
            integrationTest("include deep projects") { runGradle(it) }
        }

        testSuite("tree deserialization") {
            integrationTest("accept json files") { runGradle(it) }
            integrationTest("accept json5 files") { runGradle(it) }
            integrationTest("accept toml files") { runGradle(it) }
            integrationTest("accept yaml files") { runGradle(it) }
            integrationTest("reject unknown files") { shouldFailBuild { runGradle(it) } }
            integrationTest("define project branches") { runGradle(it) }
            integrationTest("define inverted branches") { runGradle(it) }
            integrationTest("define extended nodes") { runGradle(it) }
            integrationTest("load_files_in_action") { runGradle(it) }
        }
    }
}
