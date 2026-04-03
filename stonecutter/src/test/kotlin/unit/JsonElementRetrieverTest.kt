package unit

import com.github.ajalt.mordant.rendering.TextColors
import dev.kikugie.stonecutter.data.deserialization.Toml2JsonConverter
import dev.kikugie.stonecutter.data.deserialization.accept
import dev.kikugie.stonecutter.settings.properties.PropertiesRetriever
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.test.TestScope
import kotlinx.serialization.json.JsonElement
import org.intellij.lang.annotations.Language
import util.TestSetup

class JsonElementRetrieverTest : ShouldSpec({
    // TODO this
})

private class RetrieverSetup : TestSetup {
    @Language("toml") lateinit var data: String
    val tags: MutableSet<String> = mutableSetOf()
    val path: MutableList<String> = mutableListOf()
    private lateinit var check: (JsonElement?) -> Unit

    fun element(action: JsonElement?.() -> Unit) {
        check = action
    }

    override suspend fun TestScope.invoke() {
        val retriever = PropertiesRetriever(tags, path.joinToString(":"))
        val result = Toml2JsonConverter.convert(data).accept(retriever, "")
        println(TextColors.magenta(result.element.toString()))
        check.invoke(result.element)
    }
}
