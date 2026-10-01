@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.testSuite
import integrationTest
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
            // The disabled scope now opens an extended block here and line-comments the enclosed statement.
            line shouldBe "    //@SuppressWarnings(\"rawtypes\")"
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/21
        integrationTest("preserve line break") {
            runGradle(it, "stonecutterSwitchTo1")
            val file = it read "src/main/java/Example.java"
            val line = file.lines()[3]
            line shouldBe "        //System.out.println(\"Hello world!\");"
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/22
        // The closer stays on the last content line: it sits inside the enclosing block comment,
        // where the leading `//` opens nothing, so there is no `*/` for it to swallow.
        integrationTest("combine line scopes") {
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
        integrationTest("locate errors in file") {
            // Rework resolves issue #54: the split `/*?} elif {*/` marker in FluidType.java now processes without error.
            runGradle(it, "stonecutterSwitchTo26.1")
        }

        integrationTest("not throw npe on code builder init") {
            // Rework generates this project cleanly (previously the build failed); success still guards against the NPE.
            runGradle(it, "stonecutterGenerate").output shouldNotContain "CodeBuilder.<init>"
        }

        integrationTest("stack condition scopes") {
            runGradle(it, "stonecutterSwitchTo26.3")
            it read "src/main/java/Example.java" shouldBe """
                void main() {
                    //? if >26.2
                    //? if >26.1
                    println(1);
                    //? else
                    //println(2);
                }
            """.trimIndent()

            runGradle(it, "stonecutterSwitchTo26.2")
            it read "src/main/java/Example.java" shouldBe """
                void main() {
                    //? if >26.2
                    /*//? if >26.1
                    println(1);
                    //? else
                    //println(2);
                */}
            """.trimIndent()
        }

        integrationTest("report errors in commented") {
            // Should fail because of an invalid id
            shouldFailBuild { runGradle(it, ":26.3:stonecutterGenerate") }
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/75
        integrationTest("order tasks before active") {
            val order = runGradle(it, "compileJava").tasks.map { task -> task.path }
                .filter { path -> path.endsWith(":compileJava") }
            order shouldBe listOf(":1:compileJava", ":2:compileJava", ":3:compileJava")
        }
    }
}
