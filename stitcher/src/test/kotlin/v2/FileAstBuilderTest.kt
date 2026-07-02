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
                it.id shouldBe LAYOUT_GROUP + "unclosed-scope"
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
            shouldBeSingleton {
                it.id shouldBe LAYOUT_GROUP + "unmatched-root-closer"
                it.hint.shouldBeNull()
            }
        }
    }

    should("stop closer match at condition").config<ProcessTestSetup> {
        input = """
            //? if true
            //? if false {
            //?}
            //?}
        """.trimIndent()

        errors {
            shouldBeSingleton {
                it.id shouldBe LAYOUT_GROUP + "unmatched-root-closer"
                it.hint.shouldBeNull()
            }
        }
    }

    should("stop closer match at other type").config<ProcessTestSetup> {
        input = """
            //? if true
            //~ if true a -> b
            //?}
        """.trimIndent()

        errors {
            shouldBeSingleton {
                it.id shouldBe LAYOUT_GROUP + "unmatched-root-closer"
                it.hint.shouldNotBeNull()
            }
        }
    }

    should("suggest dangling closer fix").config<ProcessTestSetup> {
        input = """
            //? if false
            //?}
        """.trimIndent()

        errors {
            shouldBeSingleton {
                it.id shouldBe LAYOUT_GROUP + "unmatched-root-closer"
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
                it.id shouldBe LAYOUT_GROUP + "mismatched-closer-type"
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
                it.id shouldBe LAYOUT_GROUP + "lookup-not-found"
                it.hint.shouldBeNull()
            }
        }
    }

    should("report lookup search barrier").config<ProcessTestSetup> {
        input = """
            //? if false >> ':'
            ... ...
            //? if true
            :::
        """.trimIndent()

        errors {
            shouldBeSingleton {
                it.id shouldBe LAYOUT_GROUP + "lookup-not-found"
                it.hint shouldContain "stops the search"
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
                it.id shouldBe LAYOUT_GROUP + "dangling-implicit-extension"
            }
        }
    }

    should("connect line else through comments").config<ProcessTestSetup> {
        input = """
            //? if true
            
            //...
            //? else
            ...
        """.trimIndent()
    }

    should("connect line else in condition scope").config<ProcessTestSetup> {
        input = """
            //? if true {
                //? if false
                ...
                
                //? else
                //...
            //?}
        """.trimIndent()
    }

    should("connect line else in different scope").config<ProcessTestSetup> {
        input = """
            //~ if true a -> b {
                //? if false
                ...
                
                //? else
                //...
            //~}
        """.trimIndent()
    }

    should("not connect line else on closed scope").config<ProcessTestSetup> {
        input = """
            //? if true {
            //? else
            //?}
        """.trimIndent()

        errors {
            shouldBeSingleton {
                it.id shouldBe LAYOUT_GROUP + "unmatched-implicit-extension"
                it.hint shouldContain "Start this"
            }
        }
    }

    should("connect line extension preemptively").config<ProcessTestSetup> {
        input = """
            //? if true
            ...//? else
            ...
        """.trimIndent()
    }

    should("report mismatched line extension").config<ProcessTestSetup> {
        input = """
            //~ if true a -> b
            ...//? else
            ...
        """.trimIndent()

        errors {
            shouldBeSingleton {
                it.id shouldBe LAYOUT_GROUP + "unmatched-implicit-extension"
                it.hint.shouldNotBeNull()
            }
        }
    }
})