@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stonecutter.model.process.ConfigurationFlag
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

private val JSON = Json {
    prettyPrint = true
}

val ConfigurationRegistryTest by testSuite {
    test("reading does not store the default") {
        val registry = ConfigurationRegistry()
        registry[ConfigurationFlag("unset", true)] shouldBe true
        registry[ConfigurationFlag("unset", 5L)] shouldBe 5L

        JSON.encodeToString(registry) shouldBe "{}"
        registry shouldBe ConfigurationRegistry()
    }

    test("a value of another type falls back to the default") {
        val registry = JSON.decodeFromString<ConfigurationRegistry>("""{"flag": {"type": "BOOL", "default": false, "value": true}}""")

        registry[ConfigurationFlag("flag", false)] shouldBe true
        registry[ConfigurationFlag("flag", 3L)] shouldBe 3L
        registry[ConfigurationFlag("flag", "text")] shouldBe "text"
    }

    test("explicit defaults are ignored by equality") {
        val flag = ConfigurationFlag("flag", false)
        val registry = ConfigurationRegistry().apply { set(flag, false) }

        registry shouldBe ConfigurationRegistry()
        registry.hashCode() shouldBe ConfigurationRegistry().hashCode()
    }

    test("flag serialization") {
        val bool = ConfigurationFlag("my-bool", false)
        val int = ConfigurationFlag("my-int", 0L)
        val float = ConfigurationFlag("my-float", 0.0)
        val string = ConfigurationFlag("my-string", "...")

        val registry = ConfigurationRegistry()
        registry[bool] = true
        registry[int] = 0L
        registry[float] = 1.0
        registry[string] = "..."

        JSON.encodeToString(registry) shouldBe """
            {
                "my-bool": {
                    "type": "BOOL",
                    "default": false,
                    "value": true
                },
                "my-int": {
                    "type": "INT",
                    "default": 0
                },
                "my-float": {
                    "type": "FLOAT",
                    "default": 0.0,
                    "value": 1.0
                },
                "my-string": {
                    "type": "STRING",
                    "default": "..."
                }
            }
        """.trimIndent()
    }
}
