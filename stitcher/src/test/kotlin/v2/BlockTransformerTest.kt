package v2

import io.kotest.core.spec.style.ShouldSpec
import v2.impl.ProcessTestSetup
import v2.impl.config

class BlockTransformerTest : ShouldSpec({
    context("conditions") {
        should("disable line").config<ProcessTestSetup> {
            input = """
                //? if false
                ...
            """.trimIndent()

            output = """
                //? if false
                //...
            """.trimIndent()
        }

        should("enable line").config<ProcessTestSetup> {
            input = """
                //? if true
                //...
            """.trimIndent()

            output = """
                //? if true
                ...
            """.trimIndent()
        }

        should("keep line indent").config<ProcessTestSetup> {
            input = """
                ...
                    //? if false
                    ...
            """.trimIndent()

            output = """
                ...
                    //? if false
                    //...
            """.trimIndent()
        }

        should("disable scope").config<ProcessTestSetup> {
            input = """
                //? if false {
                ...
                //?}
            """.trimIndent()

            output = """
                //? if false {
                /*...
                *///?}
            """.trimIndent()
        }

        should("enable scope").config<ProcessTestSetup> {
            input = """
                //? if true {
                /*...
                *///?}
            """.trimIndent()

            output = """
                //? if true {
                ...
                //?}
            """.trimIndent()
        }

        should("switch block-block else chain").config<ProcessTestSetup> {
            input = """
                //? if false {
                ...
                //?} else {
                /*...
                *///?}
            """.trimIndent()

            output = """
                //? if false {
                /*...
                *///?} else {
                ...
                //?}
            """.trimIndent()
        }

        should("switch block-line else chain").config<ProcessTestSetup> {
            input = """
                //? if false {
                ...
                //?} else
                //...
            """.trimIndent()

            output = """
                //? if false {
                /*...
                *///?} else
                ...
            """.trimIndent()
        }

        should("switch line-line else chain").config<ProcessTestSetup> {
            input = """
                //? if false
                ...
                //? else
                //...
            """.trimIndent()

            output = """
                //? if false
                //...
                //? else
                ...
            """.trimIndent()
        }

        should("switch line-block else chain").config<ProcessTestSetup> {
            input = """
                //? if false
                ...
                //? else {
                /*...
                *///?}
            """.trimIndent()

            output = """
                //? if false
                //...
                //? else {
                ...
            """.trimIndent()
        }
    }
})