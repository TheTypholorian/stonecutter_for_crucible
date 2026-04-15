package unit

import com.github.ajalt.mordant.rendering.TextColors
import dev.kikugie.stonecutter.data.deserialization.SCElement
import dev.kikugie.stonecutter.data.deserialization.SCPrimitive
import dev.kikugie.stonecutter.data.deserialization.TomlConverter
import dev.kikugie.stonecutter.settings.properties.PropertiesRetriever
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.intellij.lang.annotations.Language
import util.TestSetup
import util.config

class PropertiesRetrieverTest : ShouldSpec({
    should("retrieve base primitive").config<RetrieverSetup> {
        data = """
            element = 1.23
        """.trimIndent()

        at("element") {
            shouldBeInstanceOf<SCPrimitive>()
            toDouble() shouldBe 1.23
        }
    }

    // https://codeberg.org/stonecutter/stonecutter/issues/51
    should("retrieve nested primitives (1)").config<RetrieverSetup> {
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
    }

    should("retrieve nested primitives (2)").config<RetrieverSetup> {
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
    }

    should("retrieve nested primitives (3)").config<RetrieverSetup> {
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
    }
})

private class RetrieverSetup : TestSetup {
    @Language("toml") lateinit var data: String
    val tags: MutableSet<String> = mutableSetOf()
    private val checks: MutableList<Check> = mutableListOf()

    fun tags(vararg tags: String): Unit = this.tags.plusAssign(tags)
    fun at(path: String, action: SCElement?.() -> Unit) {
        checks += Check(path, action)
    }

    override suspend fun TestScope.invoke() {
        val struct = TomlConverter.convert(data)
        for (check in checks) {
            val retriever = PropertiesRetriever(tags, check.path)
            val result = struct.accept(retriever, "")
            println(TextColors.magenta(result.element.toString()))
            check.action(result.element)
        }
    }

    private class Check(val path: String, val action: SCElement?.() -> Unit)
}