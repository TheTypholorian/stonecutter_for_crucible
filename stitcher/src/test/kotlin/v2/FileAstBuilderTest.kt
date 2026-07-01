package v2

import dev.kikugie.stitcher.parser.LAYOUT_GROUP
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import v2.impl.ProcessTestSetup
import v2.impl.config

class FileAstBuilderTest : ShouldSpec({
    should("close condition scope").config<ProcessTestSetup> {
        input = """
            ...
            //? if false {
            ...
            //?}
            ...
        """.trimIndent()

        output = """
            ...
            //? if false {
            /*...
            *///?}
            ...
        """.trimIndent()
    }

    should("report unclosed scope").config<ProcessTestSetup> {
        input = """
            //? if false {
            ...
        """.trimIndent()

        errors {
            shouldBeSingleton {
                it.id shouldBe LAYOUT_GROUP.child("unclosed-scope")
            }
        }
    }

    should("report dangling closer").config<ProcessTestSetup> {
        input = """
            //? if false {
            //?}
            //?}
        """.trimIndent()

        errors {
            shouldBeSingleton { it.id shouldBe LAYOUT_GROUP.child("unmatched-root-closer") }
        }
    }

    should("suggest dangling closer fix").config<ProcessTestSetup> {
        input = """
            //? if false
            //?}
        """.trimIndent()

        errors {
            shouldBeSingleton {
                it.id shouldBe LAYOUT_GROUP.child("unmatched-root-closer")
                it.hint.shouldNotBeNull { this shouldContain "may need to end with `{`" }
            }
        }
    }

    should("report mismatched closer").config<ProcessTestSetup> {
        swaps["example"] = "..."

        input = """
            //$ example {
            ...
            //?}
        """.trimIndent()

        errors {
            shouldBeSingleton {
                it.id shouldBe LAYOUT_GROUP.child("mismatched-closer-type")
            }
        }
    }

    should("use next line for line scope").config<ProcessTestSetup> {
        input = """
            //? if false
            ...
            ...
        """.trimIndent()

        output = """
            //? if false
            //...
            ...
        """.trimIndent()
    }

    should("skip empty lines for line scope").config<ProcessTestSetup> {
        input = """
            //? if false
            
            
            ...
            ...
        """.trimIndent()

        output = """
            //? if false
            
            
            //...
            ...
        """.trimIndent()
    }

    should("search comments for line scope").config<ProcessTestSetup> {
        input = """
            /*? if true*//*
            ...
            */
            ...
        """.trimIndent()

        output = """
            /*? if true*/
            ...
            
            ...
        """.trimIndent()
    }

    should("consider comment endings for line scope").config<ProcessTestSetup> {
        input = """
            //? if true
            //...
            ...
        """.trimIndent()

        output = """
            //? if true
            ...
            ...
        """.trimIndent()
    }

    should("split comments for line scope").config<ProcessTestSetup> {
        input = """
            //? if true
            /*...
            ...*/
        """.trimIndent()

        output = """
            //? if true
            ...
            /*...*/
        """.trimIndent()
    }

    should("locate word break for default lookup").config<ProcessTestSetup> {
        input = """
            //? if false >>
            ... ...
        """.trimIndent()

        output = """
            //? if false >>
            /*...*/ ...
        """.trimIndent()
    }

    should("locate word break in comments").config<ProcessTestSetup> {
        input = """
            //? if true >>
            //... ...
        """.trimIndent()

        output = """
            //? if true >>
            .../* ...*/
        """.trimIndent()
    }

    should("locate custom lookup string").config<ProcessTestSetup> {
        input = """
            //? if false >> ';'
            ... .;.
        """.trimIndent()

        output = """
            //? if false >> ';'
            /*... .*/;.
        """.trimIndent()
    }

    should("locate custom lookup capturing string").config<ProcessTestSetup> {
        input = """
            //? if false >>+ ';'
            ... .;.
        """.trimIndent()

        output = """
            //? if false >>+ ';'
            /*... .;*/.
        """.trimIndent()
    }

    should("locate custom lookup string across comments").config<ProcessTestSetup> {
        input = """
            //? if true >> ';'
            ... //.;.
        """.trimIndent()

        output = """
            //? if true >> ';'
            ... ./*;.*/
        """.trimIndent()
    }

    should("report missing lookup string").config<ProcessTestSetup> {
        input = """
            //? if false >> ':'
            ... ...
        """.trimIndent()

        errors {
            shouldBeSingleton {
                it.id shouldBe LAYOUT_GROUP.child("lookup-not-found")
            }
        }
    }

    should("report invalid line else").config<ProcessTestSetup> {
        input = """
            //? if false
            //...
            ...
            
            //? else
            ...
        """.trimIndent()

        errors {
            shouldBeSingleton {
                it.id shouldBe LAYOUT_GROUP.child("dangling-implicit-extension")
            }
        }
    }
})