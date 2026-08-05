@file:Suppress("PublicApiImplicitType")

package unit

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stonecutter.data.deserialization.SCElement
import dev.kikugie.stonecutter.data.deserialization.SCPrimitive
import dev.kikugie.stonecutter.data.deserialization.TomlConverter
import dev.kikugie.stonecutter.settings.properties.PropertiesRetriever
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.intellij.lang.annotations.Language

val PropertiesRetrieverTest by testSuite {
    test("retrieve base primitive") {
        RetrieverSetup().apply {
            data = """
                element = 1.23
            """.trimIndent()

            at("element") {
                shouldBeInstanceOf<SCPrimitive>()
                toDouble() shouldBe 1.23
            }
        }.verify()
    }

    // https://codeberg.org/stonecutter/stonecutter/issues/51
    test("retrieve nested primitives (1)") {
        RetrieverSetup().apply {
            data = """
                a.b.c = "a"

                ["1.20.1"]
                d = "d"
            """.trimIndent()
            tags("1.20.1")

            at("d") {
                shouldBeInstanceOf<SCPrimitive>()
                toString() shouldBe "d"
            }
        }.verify()
    }

    test("retrieve nested primitives (2)") {
        RetrieverSetup().apply {
            data = """
                a.b = "a"

                ["1.20.1"]
                d = "d"
            """.trimIndent()
            tags("1.20.1")

            at("d") {
                shouldBeInstanceOf<SCPrimitive>()
                toString() shouldBe "d"
            }
        }.verify()
    }

    test("retrieve nested primitives (3)") {
        RetrieverSetup().apply {
            data = """
                a.b.c = "a"

                ["1.20.1"]
                d.e = "d"
            """.trimIndent()
            tags("1.20.1")

            at("d:e") {
                shouldBeInstanceOf<SCPrimitive>()
                toString() shouldBe "d"
            }
        }.verify()
    }
}

private class RetrieverSetup {
    @Language("toml") lateinit var data: String
    val tags: MutableSet<String> = mutableSetOf()
    private val checks: MutableList<Check> = mutableListOf()

    fun tags(vararg tags: String): Unit = this.tags.plusAssign(tags)
    fun at(path: String, action: SCElement?.() -> Unit) {
        checks += Check(path, action)
    }

    fun verify() {
        val struct = TomlConverter.convert(data)
        for (check in checks) {
            val retriever = PropertiesRetriever(tags, check.path)
            val result = struct.accept(retriever, "")
            println(result.element.toString())
            check.action(result.element)
        }
    }

    private class Check(val path: String, val action: SCElement?.() -> Unit)
}
