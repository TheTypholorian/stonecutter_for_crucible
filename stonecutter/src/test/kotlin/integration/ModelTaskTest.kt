@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.paths.shouldNotExist
import io.kotest.matchers.string.shouldContain
import read
import runGradle
import tempDirFixture

val ModelTaskTest by testSuite {
    tempDirFixture() asParameterForEach {
        // With the default build directory, the branch model must land under
        // `<project>/build/stonecutter-cache/branch.json`. This holds both
        // before and after the isolated-projects fix (the paths coincide),
        // guarding the de-facto public output contract against accidental
        // relocation.
        integrationTest("default build directory") {
            runGradle(it, "stonecutterSaveBranchModel")

            it.resolve("build/stonecutter-cache/branch.json").shouldExist()
            (it read "build/stonecutter-cache/branch.json") shouldContain "\"1\""
        }

        // When the branch project relocates its buildDirectory, the model must
        // follow it. This FAILS before the fix (the output path is hardcoded to
        // `<projectDir>/build/...`) and PASSES after switching to
        // `branch.project.layout.buildDirectory`.
        integrationTest("custom build directory") {
            runGradle(it, "stonecutterSaveBranchModel")

            it.resolve("custom-build/stonecutter-cache/branch.json").shouldExist()
            it.resolve("build/stonecutter-cache/branch.json").shouldNotExist()
            (it read "custom-build/stonecutter-cache/branch.json") shouldContain "\"1\""
        }
    }
}
