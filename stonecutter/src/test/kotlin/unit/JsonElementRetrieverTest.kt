package unit

import com.github.ajalt.mordant.rendering.TextColors
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.deserialization.Toml2JsonConverter
import dev.kikugie.stonecutter.data.deserialization.accept
import dev.kikugie.stonecutter.settings.properties.JsonElementRetriever
import dev.kikugie.stonecutter.settings.properties.JsonPathStack
import dev.kikugie.stonecutter.util.SCJSON
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.intellij.lang.annotations.Language
import util.TestSetup
import util.config

class JsonElementRetrieverTest : ShouldSpec({
    should("retrieve primitives").config<RetrieverSetup> {
        target = ":1.0"
        data = """
            [group]
            key = 1
        """.trimIndent()
        path += listOf("group", "key")

        element {
            shouldBeInstanceOf<JsonPrimitive> { it.content shouldBe "1" }
        }
    }

    should("retrieve arrays").config<RetrieverSetup> {
        target = ":1.0"
        data = """
            [group]
            key = [1, 2]
        """.trimIndent()
        path += listOf("group", "key")

        element {
            shouldBeInstanceOf<JsonArray>()
        }
    }

    should("retrieve objects").config<RetrieverSetup> {
        target = ":1.0"
        data = """
            [group]
            key = {nested = "OK"}
        """.trimIndent()
        path += listOf("group", "key")

        element {
            shouldBeInstanceOf<JsonObject>()
        }
    }

    should("resolve projects").config<RetrieverSetup> {
        target = ":1.0"
        data = """
            ["1.0"]
            key = 1
        """.trimIndent()
        path += listOf("key")

        element {
            shouldBeInstanceOf<JsonPrimitive>()
        }
    }

    should("resolve dot-separated projects").config<RetrieverSetup> {
        target = ":common:1.0"
        data = """
            [common."1.0"]
            key = 1
        """.trimIndent()
        path += listOf("key")

        element {
            shouldBeInstanceOf<JsonPrimitive>()
        }
    }

    should("resolve colon-separated projects").config<RetrieverSetup> {
        target = ":common:1.0"
        data = """
            ["common:1.0"]
            key = 1
        """.trimIndent()
        path += listOf("key")

        element {
            shouldBeInstanceOf<JsonPrimitive>()
        }
    }

    should("resolve tags").config<RetrieverSetup> {
        target = ":1.0"
        data = """
            [tag]
            key = 1
        """.trimIndent()
        tags += listOf("tag")
        path += listOf("key")

        element {
            shouldBeInstanceOf<JsonPrimitive>()
        }
    }

    should("resolve combined").config<RetrieverSetup> {
        target = ":common:1.0"
        data = """
            ["common:1.0"]
            tag.key = 1
        """.trimIndent()
        tags += listOf("tag")
        path += listOf("key")

        element {
            shouldBeInstanceOf<JsonPrimitive>()
        }
    }

    should("be interrupted at a path for tags").config<RetrieverSetup> {
        target = ":common:1.0"
        data = """
            ["common:1.0"]
            key.tag = 1
        """.trimIndent()
        tags += listOf("tag")
        path += listOf("key", "tag")

        element {
            shouldBeInstanceOf<JsonPrimitive>()
        }
    }

    should("be interrupted at a path for projects").config<RetrieverSetup> {
        target = ":common:1.0"
        data = """
            key.common = 1
        """.trimIndent()
        path += listOf("key", "common")

        element {
            shouldBeInstanceOf<JsonPrimitive>()
        }
    }
})

private class RetrieverSetup : TestSetup {
    @Language("toml") lateinit var data: String
    lateinit var target: String
    var source: String = ":"

    val tags: MutableSet<String> = mutableSetOf()
    val path: MutableList<String> = mutableListOf()
    private lateinit var check: (JsonElement?) -> Unit

    fun element(action: JsonElement?.() -> Unit) {
        check = action
    }

    override suspend fun TestScope.invoke() {
        val retriever = JsonElementRetriever(path)
        val stack = JsonPathStack(ProjectHierarchy(source).relativize(ProjectHierarchy(target)).split(':'), tags)
        val result = Toml2JsonConverter.convert(data).accept(retriever, stack)
        println(TextColors.magenta(result.element.toString()))

        check.invoke(result.element)
    }
}

private fun visitor(source: String, target: String, tags: Set<String>, vararg segments: String): JsonElementRetrieverPreset =
    JsonElementRetrieverPreset(source, target, tags, segments.toList())

private class JsonElementRetrieverPreset(val segments: List<String>, val tags: Set<String>, val retriever: JsonElementRetriever) {
    constructor(source: String, target: String, tags: Set<String>, keys: List<String>) : this(
        ProjectHierarchy(source).relativize(ProjectHierarchy(target)).split(':'), tags, JsonElementRetriever(keys)
    )

    infix fun with(@Language("JSON") json: String): JsonElementRetriever.GetJsonResult =
        SCJSON.decodeFromString<JsonObject>(json).accept(retriever, JsonPathStack(segments, tags))
}
