@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.disable
import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import read
import runGradle
import shouldFailBuild
import tempDirFixture
import kotlin.io.path.useLines

val ReportedIssuesTest by testSuite {
    tempDirFixture() asParameterForEach {
        integrationTest("not insert eof") {
            runGradle(it, "stonecutterSwitchTo2")
            runGradle(it, "stonecutterSwitchTo1")
            runGradle(it, ":1:run").output shouldContain "Hello world!"
            it read "src/main/java/Example.java" shouldNotContain "<EOF>"
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/18
        integrationTest("count nested comments") {
            runGradle(it, "stonecutterSwitchTo1.14.4")
            val line = it.resolve("src/main/java/PopupScreen.kt")
                .useLines { lines -> lines.find { "renderBlurredBackground" in it } }
            line shouldBe "      /*renderBlurredBackground(/*? if <=1.21.1 {*/partialTick/*?}*/)"
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/19
        integrationTest("count insertion offsets") {
            runGradle(it, "stonecutterSwitchTo1.20.1")
            runGradle(it, "stonecutterSwitchTo1.21.6")
            val file = it read "src/main/java/WaterFogEnvironmentMixin.java"
            val line = file.lines()[28]
            line shouldBe "    /*@SuppressWarnings(\"rawtypes\")*/"
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/21
        integrationTest("preserve line break") {
            runGradle(it, "stonecutterSwitchTo1")
            val file = it read "src/main/java/Example.java"
            val line = file.lines()[3]
            line shouldBe "        //System.out.println(\"Hello world!\");"
        }

        // TODO: Unfinished
        // https://codeberg.org/stonecutter/stonecutter/issues/22
        integrationTest("combine line scopes", TestConfig.disable()) {
            runGradle(it, "stonecutterSwitchTo1")
            it read "src/main/java/Example.java" shouldBe """
                //? if >1
                /*//? if >2
                //public class Example { }*/
            """.trimIndent()
        }

        integrationTest("propagate replacement tags") {
            runGradle(it, "stonecutterSwitchTo1")
            it read "src/main/java/Example.java" shouldBe """
                public class Example {
                    String value1 = "abc";
                    //~ !my_id
                    String value1 = "abc";
                }
            """.trimIndent()

            runGradle(it, "stonecutterSwitchTo2")
            it read "src/main/java/Example.java" shouldBe """
                public class Example {
                    String value1 = "cde";
                    //~ !my_id
                    String value1 = "abc";
                }
            """.trimIndent()
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/54
        integrationTest("locate errors in file", TestConfig.disable()) {
            shouldFailBuild { runGradle(it, "stonecutterSwitchTo26.1") }.buildResult.output
                .lineSequence().find { "FluidType.java:91:94" in it }.shouldNotBeNull()
        }

        integrationTest("not throw npe on code builder init") {
            shouldFailBuild { runGradle(it, "stonecutterGenerate") }.buildResult.output shouldNotContain "CodeBuilder.<init>"
        }
    }
}
