package unit

import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.deserialization.accept
import dev.kikugie.stonecutter.settings.properties.JsonElementRetriever
import dev.kikugie.stonecutter.util.SCJSON
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.intellij.lang.annotations.Language
import java.util.*

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

    should("resolve paths") {
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
})

private fun visitor(source: String, target: String, tags: Set<String>, vararg segments: String): JsonElementRetriever =
    JsonElementRetriever(ProjectHierarchy(source), ProjectHierarchy(target), tags, segments.toList())

private infix fun JsonElementRetriever.with(@Language("JSON") json: String): JsonElementRetriever.GetJsonResult =
    SCJSON.decodeFromString<JsonObject>(json).accept(this, ArrayDeque())
