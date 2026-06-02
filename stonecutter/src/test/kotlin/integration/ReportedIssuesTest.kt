package integration

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import util.GradleSetup
import util.config
import util.read
import kotlin.io.path.useLines

class ReportedIssuesTest : ShouldSpec({
    should("not insert eof").config<GradleSetup> {
        build("stonecutterSwitchTo2")
        build("stonecutterSwitchTo1")
        build(":1:run") {
            output shouldContain "Hello world!"
            dir read "src/main/java/Example.java" shouldNotContain "<EOF>"
        }
    }

    // https://codeberg.org/stonecutter/stonecutter/issues/18
    should("count nested comments").config<GradleSetup> {
        build("stonecutterSwitchTo1.14.4") {
            val line = dir.resolve("src/main/java/PopupScreen.kt")
                .useLines { lines -> lines.find { "renderBlurredBackground" in it } }
            line shouldBe "      /*renderBlurredBackground(/*? if <=1.21.1 {*/partialTick/*?}*/)"
        }
    }

    // https://codeberg.org/stonecutter/stonecutter/issues/19
    should("count insertion offsets").config<GradleSetup> {
        build("stonecutterSwitchTo1.20.1")
        build("stonecutterSwitchTo1.21.6") {
            val file = dir read "src/main/java/WaterFogEnvironmentMixin.java"
            val line = file.lines()[28]
            line shouldBe "    /*@SuppressWarnings(\"rawtypes\")*/"
        }
    }

    // https://codeberg.org/stonecutter/stonecutter/issues/21
    should("preserve line break").config<GradleSetup> {
        build("stonecutterSwitchTo1") {
            val file = dir read "src/main/java/Example.java"
            val line = file.lines()[3]
            line shouldBe "        //System.out.println(\"Hello world!\");"
        }
    }

    // TODO: Unfinished
    // https://codeberg.org/stonecutter/stonecutter/issues/22
    xshould("combine line scopes").config<GradleSetup> {
        build("stonecutterSwitchTo1") {
            dir read "src/main/java/Example.java" shouldBe """
                //? if >1
                /*//? if >2
                //public class Example { }*/
            """.trimIndent()
        }
    }

    should("propagate replacement tags").config<GradleSetup> {
        build("stonecutterSwitchTo1") {
            dir read "src/main/java/Example.java" shouldBe """
                public class Example {
                    String value1 = "abc";
                    //~ !my_id
                    String value1 = "abc";
                }
            """.trimIndent()
        }
        build("stonecutterSwitchTo2") {
            dir read "src/main/java/Example.java" shouldBe """
                public class Example {
                    String value1 = "cde";
                    //~ !my_id
                    String value1 = "abc";
                }
            """.trimIndent()
        }
    }

    // https://codeberg.org/stonecutter/stonecutter/issues/54
    xshould("locate errors in file").config<GradleSetup> {
        fail("stonecutterSwitchTo26.1") {
            output.lineSequence().find { "FluidType.java:91:94" in it }.shouldNotBeNull()
        }
    }

    should("not throw npe on code builder init").config<GradleSetup> {
        fail("stonecutterGenerate")
    }
})