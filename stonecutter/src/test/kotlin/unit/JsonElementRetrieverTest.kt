package unit

import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.deserialization.accept
import dev.kikugie.stonecutter.settings.properties.JsonElementRetriever
import dev.kikugie.stonecutter.settings.properties.JsonPathStack
import dev.kikugie.stonecutter.util.SCJSON
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.intellij.lang.annotations.Language

class JsonElementRetrieverTest : ShouldSpec({
    should("retrieve primitives") {
        val result = visitor(":", ":1.21", emptySet(), "it") with """
            {
              "1.21": {
                "it": "Hello!"
              }
            }
        """.trimIndent()
        result.element.shouldNotBeNull {
            shouldBeInstanceOf<JsonPrimitive>()
        }
    }

    should("retrieve structs") {
        val result = visitor(":", ":1.21", emptySet(), "it") with """
            {
              "1.21": {
                "it": [ 1, 2, 3 ]
              }
            }
        """.trimIndent()
        result.element.shouldNotBeNull {
            shouldBeInstanceOf<JsonArray>()
        }
    }

    should("propagate through tags") {
        val result = visitor(":", ":1.21", setOf("tag"), "it") with """
            {
              "1.21": {
                "tag": {
                  "it": [ 1, 2, 3 ]
                }
              }
            }
        """.trimIndent()
        result.element.shouldNotBeNull {
            shouldBeInstanceOf<JsonArray>()
        }
    }

    should("resolve projects") {
        val result = visitor(":", ":1.21", emptySet(), "it") with """
            {
              "it": "Wrong!",
              "1.21": {
                "it": "OK"
              }
            }
        """.trimIndent()
        result.element.shouldNotBeNull {
            shouldBeInstanceOf<JsonPrimitive>()
            toString() shouldBe "\"OK\""
        }
    }

    should("resolve semicolon paths") {
        val result = visitor(":", ":fabric:1.21", emptySet(), "item") with """
            {
              "fabric:1.21": {
                "item": "Hello!"
              }
            }
        """.trimIndent()
        result.element.shouldNotBeNull {
            shouldBeInstanceOf<JsonPrimitive>()
        }
    }
})

private fun visitor(source: String, target: String, tags: Set<String>, vararg segments: String): JsonElementRetrieverPreset =
    JsonElementRetrieverPreset(source, target, tags, segments.toList())

private class JsonElementRetrieverPreset(val segments: List<String>, val tags: Set<String>, val retriever: JsonElementRetriever) {
    constructor(source: String, target: String, tags: Set<String>, keys: List<String>) : this(
        ProjectHierarchy(source).relativize(ProjectHierarchy(target)).split(':'), tags, JsonElementRetriever(keys)
    )

    infix fun with(@Language("JSON") json: String): JsonElementRetriever.GetJsonResult =
        SCJSON.decodeFromString<JsonObject>(json).accept(retriever, JsonPathStack(segments, tags))
}
