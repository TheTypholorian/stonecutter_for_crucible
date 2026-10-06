@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.stonecutter.model.SCHEMA_FIELD
import dev.kikugie.stonecutter.model.process.*
import dev.kikugie.stonecutter.model.tree.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.regex.Pattern
import kotlin.io.path.Path

private val JSON = Json { prettyPrint = true }

/** Round-trips [value] through Java serialization, the form used to cross a Gradle worker boundary. */
private inline fun <reified T : Serializable> T.viaJava(): T {
    val bytes = ByteArrayOutputStream().also { out ->
        ObjectOutputStream(out).use { it.writeObject(this) }
    }.toByteArray()

    return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }.shouldBeInstanceOf<T>()
}

private fun parameters() = ProcessParameters(
    constants = mapOf("fabric" to true, "neoforge" to false),
    targets = mapOf("minecraft" to SemanticVersion(listOf(1, 21, 4))),
    swaps = mapOf("mc_version" to "\"1.21.4\""),
    replacements = listOf(
        ReplacementModel(StringReplacement("GuiGraphics", "GuiGraphicsExtractor"), enabled = true),
        ReplacementModel(RegexReplacement("""(\w+)Screen""", "$1Menu", flags = 2, identifier = "screens"), enabled = false),
        ReplacementModel(
            PerlReplacement("""\bfoo\b""", "bar", identifier = "perl"), enabled = true,
            includes = setOf("java/**"), excludes = setOf("**/Ignored.java")
        ),
    ),
)

val ModelSerializationTest by testSuite {
    testSuite("process parameters") {
        test("survives json") {
            val value = parameters()
            JSON.decodeFromString<ProcessParameters>(JSON.encodeToString(value)) shouldBe value
        }

        test("survives java serialization") {
            val value = parameters()
            value.viaJava() shouldBe value
        }

        test("keeps each replacement kind apart") {
            val decoded = JSON.decodeFromString<ProcessParameters>(JSON.encodeToString(parameters()))

            decoded.replacements[0].replacement.shouldBeInstanceOf<StringReplacement>()
            decoded.replacements[1].replacement.shouldBeInstanceOf<RegexReplacement> { it.flags shouldBe 2 }
            decoded.replacements[2].replacement.shouldBeInstanceOf<PerlReplacement> { it.identifier shouldBe "perl" }
        }

        test("keeps replacement state and filters") {
            val decoded = JSON.decodeFromString<ProcessParameters>(JSON.encodeToString(parameters()))

            decoded.replacements.map(ReplacementModel::enabled) shouldBe listOf(true, false, true)
            with(decoded.replacements[2]) {
                includes shouldBe setOf("java/**")
                excludes shouldBe setOf("**/Ignored.java")
            }
        }

        test("an empty value survives too") {
            val value = ProcessParameters()
            JSON.decodeFromString<ProcessParameters>(JSON.encodeToString(value)) shouldBe value
        }
    }

    testSuite("tree models") {
        val node = NodeInfo("1.21.4", "1.21.4", active = true, path = Path("versions/1.21.4"))
        val branch = BranchInfo("fabric", Path("fabric"))

        test("node info survives json") {
            JSON.decodeFromString<NodeInfo>(JSON.encodeToString(node)) shouldBe node
        }

        test("node model survives json") {
            val value = NodeModel("1.21.4", "1.21.4", true, branch, Path("."), parameters())
            JSON.decodeFromString<NodeModel>(JSON.encodeToString(value)) shouldBe value
        }

        test("branch model survives json") {
            val value = BranchModel("fabric", Path("fabric"), listOf(node))
            JSON.decodeFromString<BranchModel>(JSON.encodeToString(value)) shouldBe value
        }

        test("tree model survives json") {
            val value = TreeModel("0.10", "1.21.4", "1.21.4", listOf(branch), listOf(node))
            JSON.decodeFromString<TreeModel>(JSON.encodeToString(value)) shouldBe value
        }

        test("model version is the first field") {
            val encoded = listOf(
                JSON.encodeToJsonElement(NodeModel("1.21.4", "1.21.4", true, branch, Path("."), parameters())),
                JSON.encodeToJsonElement(BranchModel("fabric", Path("fabric"), listOf(node))),
                JSON.encodeToJsonElement(TreeModel("0.10", "1.21.4", "1.21.4", listOf(branch), listOf(node))),
            )
            for (it in encoded) it.jsonObject.entries.first().toPair() shouldBe (SCHEMA_FIELD to JsonPrimitive(2))
        }

        test("paths are written with forward slashes") {
            JSON.encodeToString(branch) shouldContainPath "fabric"
        }
    }

    testSuite("legacy models") {
        val branch = BranchInfo("", Path("/project"))
        val legacy09Node = """
            {
              "project": "1.21.4", "active": true,
              "branch": {"id": "", "path": "/project"}, "root": "/project",
              "parameters": {
                "dependencies": {"minecraft": {"type": "dev.kikugie.semver.data.SemanticVersion", "components": [1, 21, 4]}},
                "replacements": [
                  {"type": "dev.kikugie.stitcher.replacement.StringReplacement", "target": "B", "sources": ["A"], "identifier": "on", "tags": ["DEFAULT_ENABLED"]},
                  {"type": "dev.kikugie.stitcher.replacement.RegexReplacement", "target": "b", "pattern": "a+", "options": 2, "identifier": "off"}
                ]
              }
            }
        """.trimIndent()
        val expected09Node = NodeModel(
            "1.21.4", "1.21.4", true, branch, Path("/project"), ProcessParameters(
                targets = mapOf("minecraft" to SemanticVersion(listOf(1, 21, 4))),
                replacements = listOf(
                    ReplacementModel(StringReplacement("A", "B", "on"), enabled = true),
                    ReplacementModel(RegexReplacement("a+", "b", 2, "off"), enabled = false),
                )
            )
        )

        test("0.7 node expands sources and drops phases") {
            val json = """
                {
                  "project": "1.20.1", "branch": {"id": "", "path": "/project"}, "root": "/project",
                  "parameters": {
                    "swaps": {"mc": "\"1.20.1\""}, "constants": {"fabric": true},
                    "dependencies": {"minecraft": {"type": "dev.kikugie.semver.data.SemanticVersion", "components": [1, 20, 1]}},
                    "replacements": [
                      {"type": "dev.kikugie.stitcher.data.replacement.StringReplacement", "sources": ["Foo", "Bar"], "target": "Baz", "phase": "FIRST"},
                      {"type": "dev.kikugie.stitcher.data.replacement.RegexReplacement", "pattern": "a+", "target": "b", "phase": "LAST", "identifier": null}
                    ]
                  }
                }
            """.trimIndent()

            JSON.decodeFromString<NodeModel>(json) shouldBe NodeModel(
                "1.20.1", "1.20.1", false, branch, Path("/project"), ProcessParameters(
                    constants = mapOf("fabric" to true),
                    targets = mapOf("minecraft" to SemanticVersion(listOf(1, 20, 1))),
                    swaps = mapOf("mc" to "\"1.20.1\""),
                    replacements = listOf(
                        ReplacementModel(StringReplacement("Foo", "Baz"), enabled = true),
                        ReplacementModel(StringReplacement("Bar", "Baz"), enabled = true),
                        ReplacementModel(RegexReplacement("a+", "b"), enabled = true),
                    )
                )
            )
        }

        test("0.7 tree converts string flags and drops the current provider") {
            val json = """
                {
                  "stonecutter": "0.7.11", "vcs": "1.21.4", "current": "1.21.4",
                  "branches": [{"id": "", "path": "/project"}],
                  "nodes": [{"project": "1.21.4", "path": "/project/versions/1.21.4"}],
                  "flags": {"auto_apply_plugin": "false", "implicit_receiver": "fabric"},
                  "current_provider": "name@1.21.4"
                }
            """.trimIndent()

            val tree = JSON.decodeFromString<TreeModel>(json)
            tree.branches shouldBe listOf(branch)
            tree.nodes shouldBe listOf(NodeInfo("1.21.4", path = Path("/project/versions/1.21.4")))
            tree.flags[ConfigurationFlag("auto_apply_plugin", true)] shouldBe false
            tree.flags[ConfigurationFlag("implicit_receiver", "minecraft")] shouldBe "fabric"
        }

        test("0.8 regex options become pattern flags") {
            val json = """
                {
                  "project": "1.21.4", "branch": {"id": "", "path": "/project"}, "root": "/project",
                  "parameters": {
                    "replacements": [
                      {"type": "dev.kikugie.stitcher.transform.replacement.RegexReplacement", "target": "b", "pattern": "a+", "flags": ["IGNORE_CASE"], "identifier": "named"}
                    ]
                  }
                }
            """.trimIndent()

            JSON.decodeFromString<NodeModel>(json).parameters.replacements shouldBe listOf(
                ReplacementModel(RegexReplacement("a+", "b", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE, "named"), enabled = false)
            )
        }

        test("0.9 node reads tags and options") {
            JSON.decodeFromString<NodeModel>(legacy09Node) shouldBe expected09Node
        }

        test("schema 1 takes the 0.9 path") {
            JSON.decodeFromString<NodeModel>("{\"$SCHEMA_FIELD\": 1, ${legacy09Node.removePrefix("{")}") shouldBe expected09Node
        }

        test("0.10 alpha node reads default_enabled and node flags") {
            val json = """
                {
                  "project": "1.21.4", "branch": {"id": "", "path": "/project"}, "root": "/project",
                  "parameters": {
                    "replacements": [
                      {"type": "dev.kikugie.stonecutter.model.process.StringReplacement", "pattern": "A", "target": "B", "identifier": "on", "default_enabled": true}
                    ]
                  },
                  "flags": {"some_flag": {"type": "BOOL", "default": false, "value": true}}
                }
            """.trimIndent()

            val node = JSON.decodeFromString<NodeModel>(json)
            node.parameters.replacements shouldBe listOf(ReplacementModel(StringReplacement("A", "B", "on"), enabled = true))
            node.flags[ConfigurationFlag("some_flag", false)] shouldBe true
        }

        test("current shape without a schema decodes unchanged") {
            val node = NodeModel("1.21.4", "1.21.4", true, branch, Path("."), parameters())
            val tree = TreeModel("0.10", "1.21.4", "1.21.4", listOf(branch), listOf(NodeInfo("1.21.4", path = Path("."))))
            val model = BranchModel("fabric", Path("fabric"), listOf(NodeInfo("1.21.4", path = Path("."))))

            JSON.decodeFromJsonElement<NodeModel>(JSON.encodeToJsonElement(node).withoutSchema()) shouldBe node
            JSON.decodeFromJsonElement<TreeModel>(JSON.encodeToJsonElement(tree).withoutSchema()) shouldBe tree
            JSON.decodeFromJsonElement<BranchModel>(JSON.encodeToJsonElement(model).withoutSchema()) shouldBe model
        }

        test("newer schema is rejected") {
            shouldThrow<SerializationException> {
                JSON.decodeFromString<NodeModel>("{\"$SCHEMA_FIELD\": 99, ${legacy09Node.removePrefix("{")}")
            }
        }
    }

    testSuite("configuration registry") {
        test("survives json with every flag type") {
            val registry = ConfigurationRegistry()
            registry[ConfigurationFlag("bool", false)] = true
            registry[ConfigurationFlag("long", 0L)] = 7L
            registry[ConfigurationFlag("double", 0.0)] = 1.5
            registry[ConfigurationFlag("string", "")] = "value"

            val decoded = JSON.decodeFromString<ConfigurationRegistry>(JSON.encodeToString(registry))
            decoded[ConfigurationFlag("bool", false)] shouldBe true
            decoded[ConfigurationFlag("long", 0L)] shouldBe 7L
            decoded[ConfigurationFlag("double", 0.0)] shouldBe 1.5
            decoded[ConfigurationFlag("string", "")] shouldBe "value"
        }
    }
}

private fun JsonElement.withoutSchema(): JsonObject = JsonObject(jsonObject - SCHEMA_FIELD)

private infix fun String.shouldContainPath(path: String) {
    (path in this) shouldBe true
    ("\\" in this) shouldBe false
}
