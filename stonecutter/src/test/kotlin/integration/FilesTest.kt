@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.paths.shouldNotExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import read
import runGradle
import tempDirFixture
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

private const val BUILD: String = "versions/2/build"
private const val GENERATED: String = "$BUILD/generated/stonecutter/main"

val FilesTest by testSuite {
    tempDirFixture() asParameterForEach {
        integrationTest("disable matching files") {
            runGradle(it, ":2:classes")
            it read "$GENERATED/java/Disabled.java" shouldBe """
                /*!stonecutter-disabled
                public class Disabled {}
                */
            """.trimIndent()
            it read "$GENERATED/resources/data.cfg" shouldBe """
                #!stonecutter-disabled
                #value = 1

            """.trimIndent()

            it.resolve("$BUILD/classes/java/main/Kept.class").shouldExist()
            it.resolve("$BUILD/resources/main/kept.cfg").shouldExist()
            it.resolve("$BUILD/resources/main/data.cfg").shouldNotExist()
        }

        integrationTest("disable nested files") {
            runGradle(it, ":2:classes")
            it.resolve("$BUILD/classes/java/main/com/example/Common.class").shouldExist()
            it.resolve("$BUILD/classes/java/main/com/example/platform/neoforge/NeoforgePlatform.class").shouldExist()
            it.resolve("$BUILD/classes/java/main/com/example/platform/fabric/FabricPlatform.class").shouldNotExist()
        }

        integrationTest("keep matching files outside stonecutter roots") {
            it.resolve("versions/2/output/extra/Generated.java").createParentDirectories()
                .writeText("public class Generated {}")
            runGradle(it, ":1:classes", ":2:classes")

            val active = "versions/1/output/classes/java/main"
            it.resolve("$active/Kept.class").shouldExist()
            it.resolve("$active/External.class").shouldExist()
            it.resolve("$active/Disabled.class").shouldNotExist()

            val inactive = "versions/2/output/classes/java/main"
            it.resolve("$inactive/Kept.class").shouldExist()
            it.resolve("$inactive/Generated.class").shouldExist()
            it.resolve("$inactive/External.class").shouldExist()
            it.resolve("$inactive/Disabled.class").shouldNotExist()
        }

        integrationTest("enable matching files") {
            runGradle(it, ":2:classes")
            it read "$GENERATED/java/Enabled.java" shouldBe "public class Enabled {}\n"
            it.resolve("$BUILD/classes/java/main/Enabled.class").shouldExist()
        }

        integrationTest("skip excluded files") {
            runGradle(it, ":2:stonecutterGenerate")
            it read "$GENERATED/java/Excluded.java" shouldBe (it read "src/main/java/Excluded.java")
            it read "$GENERATED/java/Included.java" shouldContain "//String value"
        }

        integrationTest("apply deprecated filters") {
            runGradle(it, ":2:stonecutterGenerate")
            it read "$GENERATED/java/Excluded.java" shouldBe (it read "src/main/java/Excluded.java")
            it read "$GENERATED/java/Included.java" shouldContain "//String value"
        }

        integrationTest("serialize file filters") {
            runGradle(it, ":2:stonecutterSaveNodeModel")
            val model = it read "$BUILD/stonecutter-cache/node.json"
            model shouldContain "java/Skipped.java"
            model shouldContain "java/Disabled.java"
            model shouldContain "java/Enabled.java"
        }
    }
}
