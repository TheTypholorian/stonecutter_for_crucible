import dev.kikugie.stonecutter.model.process.ConfigurationFlag
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

private val JSON = Json {
    prettyPrint = true
}

class ConfigurationRegistryTest : FunSpec({
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

})
