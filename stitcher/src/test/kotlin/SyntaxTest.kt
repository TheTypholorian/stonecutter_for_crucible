import dev.kikugie.semver.data.SemanticVersion
import io.kotest.assertions.shouldFail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import util.process

class SyntaxTest : FreeSpec({
    "basic scopes" - {
        "disable closed" {
            @Language("JAVA") val content = """
                //? if false {
                int one = 1;
                int two = 2;
                //?}
            """.trimIndent()
            @Language("JAVA") val expected = """
                //? if false {
                /*int one = 1;
                int two = 2;
                *///?}
            """.trimIndent()
            process(content) shouldBe expected
        }

        "enable closed" {
            @Language("JAVA") val content = """
                //? if true {
                //int one = 1;
                //int two = 2;
                //?}
            """.trimIndent()
            @Language("JAVA") val expected = """
                //? if true {
                int one = 1;
                int two = 2;
                //?}
            """.trimIndent()
            process(content) shouldBe expected
        }

        "disable line" {
            @Language("JAVA") val content = """
                //? if false
                int one = 1;
                int two = 2;
            """.trimIndent()
            @Language("JAVA") val expected = """
                //? if false
                //int one = 1;
                int two = 2;
            """.trimIndent()
            process(content) shouldBe expected
        }

        "enable line" {
            @Language("JAVA") val content = """
                //? if true
                //int one = 1;
                int two = 2;
            """.trimIndent()
            @Language("JAVA") val expected = """
                //? if true
                int one = 1;
                int two = 2;
            """.trimIndent()
            process(content) shouldBe expected
        }

        "disable word" {
            @Language("JAVA") val content = """
                //? if false >>
                int one = 1;
                int two = 2;
            """.trimIndent()
            @Language("JAVA") val expected = """
                //? if false >>
                /*int*/ one = 1;
                int two = 2;
            """.trimIndent()
            process(content) shouldBe expected
        }

        "enable word" {
            @Language("JAVA") val content = """
                //? if true >>
                /*int*/ one = 1;
                int two = 2;
            """.trimIndent()
            @Language("JAVA") val expected = """
                //? if true >>
                int one = 1;
                int two = 2;
            """.trimIndent()
            process(content) shouldBe expected
        }
    }

    "lookup" - {
        "disable non-capturing" {
            @Language("JAVA") val content = """
                //? if false >> '='
                int one = 1;
                int two = 2;
            """.trimIndent()
            @Language("JAVA") val expected = """
                //? if false >> '='
                /*int one */= 1;
                int two = 2;
            """.trimIndent()
            process(content) shouldBe expected
        }

        "enable non-capturing" {
            @Language("JAVA") val content = """
                //? if true >> '='
                /*int one */= 1;
                int two = 2;
            """.trimIndent()
            @Language("JAVA") val expected = """
                //? if true >> '='
                int one = 1;
                int two = 2;
            """.trimIndent()
            process(content) shouldBe expected
        }

        "disable capturing" {
            @Language("JAVA") val content = """
                //? if false >>+ '='
                int one = 1;
                int two = 2;
            """.trimIndent()
            @Language("JAVA") val expected = """
                //? if false >>+ '='
                /*int one =*/ 1;
                int two = 2;
            """.trimIndent()
            process(content) shouldBe expected
        }

        "enable capturing" {
            @Language("JAVA") val content = """
                //? if true >>+ '='
                /*int one =*/ 1;
                int two = 2;
            """.trimIndent()
            @Language("JAVA") val expected = """
                //? if true >>+ '='
                int one = 1;
                int two = 2;
            """.trimIndent()
            process(content) shouldBe expected
        }

        "fail for not found" {
            @Language("JAVA") val content = """
                //? if false >> '+'
                int one = 1;
                int two = 2;
            """.trimIndent()
            shouldFail { process(content) }
        }

        "search comments" {
            @Language("JAVA") val content = """
                //? if true >>+ '2;'
                //int one = 1;
                //int two = 2;
                // unused
            """.trimIndent()
            @Language("JAVA") val expected = """
                //? if true >>+ '2;'
                int one = 1;
                int two = 2;
                // unused
            """.trimIndent()
            process(content) shouldBe expected
        }

        "split comment" {
            @Language("JAVA") val content = """
                //? if true >>+ 'two ='
                //int one = 1;
                //int two = 2;
                // unused
            """.trimIndent()
            @Language("JAVA") val expected = """
                //? if true >>+ 'two ='
                int one = 1;
                int two = /*2;*/
                // unused
            """.trimIndent()
            process(content) shouldBe expected
        }
    }

    "extended line" - {
        "search comments" {
            @Language("JAVA") val content = """
                //? if true
                /*    */
                //
                //int one = 1;
                //int two = 2;
            """.trimIndent()
            @Language("JAVA") val expected = """
                //? if true
                    
                
                int one = 1;
                //int two = 2;
            """.trimIndent()
            process(content) shouldBe expected
        }

        "split comment" {
            @Language("JAVA") val content = """
                //? if true
                /*
                
                int one = 1;
                int two = 2;
                *///unused
            """.trimIndent()
            @Language("JAVA") val expected = """
                //? if true
                
                
                int one = 1;
                /*int two = 2;
                *///unused
            """.trimIndent()
            process(content) shouldBe expected
        }

        "split comment indent" {
            @Language("JAVA") val content = """
                hello();
                    //? if true
                    /*int one = 1;
                    int two = 2;
                    */
            """.trimIndent()
            @Language("JAVA") val expected = """
                hello();
                    //? if true
                    int one = 1;
                    /*int two = 2;
                    */
            """.trimIndent()
            process(content) shouldBe expected
        }

        "excluded line break" {
            @Language("JAVA") val content = """
                //? if true
                /*int one = 1;*/
                //unused
            """.trimIndent()
            @Language("JAVA") val expected = """
                //? if true
                int one = 1;
                //unused
            """.trimIndent()
            process(content) shouldBe expected
        }
    }

    "token grouping" - {
        "version core spaces" {
            val content = "//? 1 . 1 \nok"
            shouldFail { process(content) { dependencies[""] = SemanticVersion(1, 0) } }
        }

        "pre-release spaces" {
            val content = "//? 1.1-alpha . beta \nok"
            shouldFail { process(content) { dependencies[""] = SemanticVersion(1, 0) } }
        }

        "build metadata spaces" {
            val content = "//? 1.1+alpha . beta \nok"
            shouldFail { process(content) { dependencies[""] = SemanticVersion(1, 0) } }
        }

        "semver components spaces" {
            val content = "//? 1.1 - pre-release + build-metadata \nok"
            shouldFail { process(content) { dependencies[""] = SemanticVersion(1, 0) } }
        }
    }

    "operator associativity" {
        val content = """
            //? !neoforge || <1.20.5
            ---
        """.trimIndent()
        process(content) {
            constants["neoforge"] = false
            dependencies[""] = SemanticVersion(1, 20, 1)
        } shouldBe content
    }
})