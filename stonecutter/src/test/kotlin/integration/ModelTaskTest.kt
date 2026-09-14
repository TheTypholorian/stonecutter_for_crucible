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
        // Main branch, default build dir: the model lands under
        // build/stonecutter-cache/branch.json.
        integrationTest("default build directory") {
            runGradle(it, "stonecutterSaveBranchModel")

            it.resolve("build/stonecutter-cache/branch.json").shouldExist()
            (it read "build/stonecutter-cache/branch.json") shouldContain "\"1\""
        }

        // Main branch with a relocated build dir: the model follows it.
        integrationTest("custom build directory") {
            runGradle(it, "stonecutterSaveBranchModel")

            it.resolve("custom-build/stonecutter-cache/branch.json").shouldExist()
            it.resolve("build/stonecutter-cache/branch.json").shouldNotExist()
            (it read "custom-build/stonecutter-cache/branch.json") shouldContain "\"1\""
        }

        // A non-main branch is a separate project; its model is written under
        // its own build dir by a task registered on the branch project, not the
        // controller.
        integrationTest("non main branch default directory") {
            runGradle(it, "stonecutterSaveBranchModel")

            it.resolve("build/stonecutter-cache/branch.json").shouldExist()
            it.resolve("experimental/build/stonecutter-cache/branch.json").shouldExist()
            (it read "experimental/build/stonecutter-cache/branch.json") shouldContain "experimental"
        }

        // Non-main branch with a relocated build dir: the model follows it.
        integrationTest("non main branch custom directory") {
            runGradle(it, "stonecutterSaveBranchModel")

            it.resolve("experimental/custom-build/stonecutter-cache/branch.json").shouldExist()
            it.resolve("experimental/build/stonecutter-cache/branch.json").shouldNotExist()
            (it read "experimental/custom-build/stonecutter-cache/branch.json") shouldContain "experimental"
        }

        // Same non-main branch, isolated projects on: must still work with no
        // cross-project access.
        integrationTest("non main branch isolated") {
            runGradle(it, "stonecutterSaveBranchModel")

            it.resolve("experimental/build/stonecutter-cache/branch.json").shouldExist()
            (it read "experimental/build/stonecutter-cache/branch.json") shouldContain "experimental"
        }
    }
}
