package unit

import com.github.ajalt.mordant.rendering.TextColors
import dev.kikugie.commons.collections.present
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.deserialization.Toml2JsonConverter
import dev.kikugie.stonecutter.data.deserialization.accept
import dev.kikugie.stonecutter.settings.properties.JsonPathStack
import dev.kikugie.stonecutter.settings.properties.JsonPropertyCollector
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.maps.shouldContainKeys
import io.kotest.matchers.maps.shouldNotContainKeys
import org.intellij.lang.annotations.Language
import util.TestSetup
import util.config

class JsonPropertyCollectorTest : ShouldSpec({
    should("flatten keys").config<CollectorSetup> {
        target = ":1.0"
        data = """
            key = "Hello"
            nested.key = "World"
            nested.list = [1, 2]
        """.trimIndent()
        shouldHave += listOf("key", "nested.key", "nested.list.0", "nested.list.1")
        shouldNotHave += listOf("nested", "nested.list")
    }

    should("resolve tags").config<CollectorSetup> {
        target = ":1.0"
        data = """
            key = "Hello"
            tag1.tag2.key2 = "World"
        """.trimIndent()
        tags += listOf("tag1", "tag2")
        shouldHave += listOf("key", "key2")
        shouldNotHave += listOf("tag1.tag2.key2", "tag2.key2")
    }

    should("resolve projects").config<CollectorSetup> {
        target = ":common:1.0"
        data = """
            key = 1
            
            [common]
            common_key = 2
            
            [common."1.0"]
            dot_nested_key = 3
            
            ["common:1.0"]
            colon_nested_key = 3
        """.trimIndent()
        shouldHave += listOf("key", "common_key", "dot_nested_key", "colon_nested_key")
        shouldNotHave += listOf("common.1.0.dot_nested_key", "common:1.0.colon_nested_key")
    }

    should("combine projects and keys").config<CollectorSetup> {
        target = ":common:1.0"
        data = """
            [common]
            tag.key1 = 1
            tag."1.0".key2 = 2
            
            [tag]
            common."1.0".key3 = 3
        """.trimIndent()
        tags += "tag"
        shouldHave += listOf("key1", "1.0.key2", "key3")
        shouldNotHave += listOf("key2")
    }

    should("ignore late tags").config<CollectorSetup> {
        target = ":common:1.0"
        data = """
            [common]
            nest.tag = 1
        """.trimIndent()
        tags += "tag"
        shouldHave += listOf("nest.tag")
        shouldNotHave += listOf("nest")
    }
})

private class CollectorSetup : TestSetup {
    @Language("toml") lateinit var data: String
    lateinit var target: String
    var source: String = ":"

    val tags: MutableSet<String> = mutableSetOf()
    val shouldHave: MutableSet<String> = mutableSetOf()
    val shouldNotHave: MutableSet<String> = mutableSetOf()

    override suspend fun TestScope.invoke() {
        val map = collect()

        println(TextColors.magenta(map.present()))
        if (shouldHave.isNotEmpty()) map.shouldContainKeys(*shouldHave.toTypedArray())
        if (shouldNotHave.isNotEmpty()) map.shouldNotContainKeys(*shouldNotHave.toTypedArray())
    }

    private fun collect(): Map<String, String> = buildMap {
        val collector = JsonPropertyCollector(::set)
        val stack = JsonPathStack(ProjectHierarchy(source).relativize(ProjectHierarchy(target)).split(':'), tags)
        Toml2JsonConverter.convert(data).accept(collector, stack)
    }
}
