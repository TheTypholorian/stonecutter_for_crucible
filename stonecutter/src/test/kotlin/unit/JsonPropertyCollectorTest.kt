package unit

import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.deserialization.accept
import dev.kikugie.stonecutter.settings.properties.JsonPropertyCollector
import dev.kikugie.stonecutter.util.SCJSON
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldContainKeys
import io.kotest.matchers.maps.shouldNotContainKey
import kotlinx.serialization.json.JsonObject
import org.intellij.lang.annotations.Language
import java.util.*

class JsonPropertyCollectorTest : ShouldSpec({
    should("resolve project paths") {
        val properties = visitor(":", ":1.21") with """
            {
              "base": "Hello",
              "1.21": {
                "nest": "World"
              }
            }
        """.trimIndent()
        properties.shouldContainKeys("base", "nest")
    }

    should("resolve tags and paths") {
        val properties = visitor(":", ":1.21", "tag") with """
            {
              "tag": {
                "base": "Hello",
                "1.21": {
                  "baser": "?"
                }
              },
              "1.21": {
                "nest": "World",
                "tag": {
                  "deep": "!"
                }
              }
            }
        """.trimIndent()
        properties.shouldContainKeys("base", "nest", "deep", "baser")
    }

    should("interrupt paths by tags") {
        val properties = visitor(":", ":fabric:1.21", "tag") with """
            {
              "fabric": {
                "tag": {
                  "1.21": {
                    "nest": "Hello"
                  }
                }
              }
            }
        """.trimIndent()
        properties.shouldNotContainKey("nest")
        properties.shouldContainKey("1.21.nest")
    }

    should("ignore late tag keys") {
        val properties = visitor(":", ":1.21", "tag") with """
            {
              "1.21": {
                "inter": {
                  "tag": "Hello"
                }
              }
            }
        """.trimIndent()
        properties.shouldContainKey("inter.tag")
    }

    should("flatten array indexes") {
        val properties = visitor(":", ":1.21") with """
            {
              "list": [1, 2, 3]
            }
        """.trimIndent()
        properties.shouldNotContainKey("list")
        properties.shouldContainKeys("list.0", "list.1", "list.2")
    }
})

private val DO_NOTHING: (String, String) -> Unit = { _, _ -> }

private fun visitor(source: String, target: String, vararg tags: String): JsonPropertyCollector =
    JsonPropertyCollector(ProjectHierarchy(source), ProjectHierarchy(target), tags.toSet(), DO_NOTHING)

private fun JsonPropertyCollector.copy(consumer: (String, String) -> Unit) =
    JsonPropertyCollector(this.segments, this.tags, consumer)

private infix fun JsonPropertyCollector.with(@Language("JSON") json: String): Map<String, String> = buildMap {
    val copy = copy(consumer = ::set)
    SCJSON.decodeFromString<JsonObject>(json).accept(copy, ArrayDeque())
}