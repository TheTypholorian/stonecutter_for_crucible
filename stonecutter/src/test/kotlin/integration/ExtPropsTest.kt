@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import runGradle
import tempDirFixture

val ExtPropsTest by testSuite {
    tempDirFixture() asParameterForEach {
        integrationTest("read default in settings") { runGradle(it) }
        integrationTest("read default in controller") { runGradle(it) }
        integrationTest("read default in parameters") { runGradle(it) }
        integrationTest("read default in build") { runGradle(it) }
        integrationTest("append new properties") { runGradle(it) }
        integrationTest("use diff props") { runGradle(it) }
        integrationTest("use property tags") { runGradle(it) }
        // https://codeberg.org/stonecutter/stonecutter/issues/44
        integrationTest("use nested property tags") { runGradle(it) }
        integrationTest("accept json files") { runGradle(it) }
        integrationTest("accept yaml files") { runGradle(it) }
        integrationTest("use typed properties") { runGradle(it) }
        integrationTest("parse property types") { runGradle(it) }
        integrationTest("retain all tags") { runGradle(it) }
        integrationTest("expose to extra") { runGradle(it) }
        integrationTest("hide when disabled") { runGradle(it) }
    }
}
