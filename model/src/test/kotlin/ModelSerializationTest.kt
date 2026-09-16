@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.stonecutter.model.process.*
import dev.kikugie.stonecutter.model.tree.*
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
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
        StringReplacement("GuiGraphics", "GuiGraphicsExtractor"),
        RegexReplacement("""(\w+)Screen""", "$1Menu", flags = 2, identifier = "screens"),
        PerlReplacement("""\bfoo\b""", "bar", identifier = "!perl", defaultEnabled = true),
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

            decoded.replacements[0].shouldBeInstanceOf<StringReplacement>()
            decoded.replacements[1].shouldBeInstanceOf<RegexReplacement> { it.flags shouldBe 2 }
            decoded.replacements[2].shouldBeInstanceOf<PerlReplacement> {
                it.identifier shouldBe "!perl"
                it.defaultEnabled shouldBe true
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

        test("node model keeps replacement filters") {
            val filters: Map<Int, ReplacementFilter> = mapOf(1 to ReplacementFilter(includes = setOf("java/**"), excludes = setOf("**/Ignored.java")))
            val value = NodeModel("1.21.4", "1.21.4", true, branch, Path("."), parameters(), replacementFilters = filters)

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

        test("paths are written with forward slashes") {
            JSON.encodeToString(branch) shouldContainPath "fabric"
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

private infix fun String.shouldContainPath(path: String) {
    (path in this) shouldBe true
    ("\\" in this) shouldBe false
}
