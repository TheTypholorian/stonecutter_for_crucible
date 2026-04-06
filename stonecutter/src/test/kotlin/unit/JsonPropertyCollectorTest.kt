@file:OptIn(StonecutterInternalAPI::class)

package unit

import com.github.ajalt.mordant.rendering.TextColors
import dev.kikugie.commons.collections.present
import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.data.deserialization.FromTomlConverter
import dev.kikugie.stonecutter.settings.properties.PropertiesCollector
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.maps.shouldContainKeys
import io.kotest.matchers.maps.shouldNotContainKeys
import org.intellij.lang.annotations.Language
import util.TestSetup
import util.config

class JsonPropertyCollectorTest : ShouldSpec({
    should("flatten nested keys").config<CollectorSetup> {
        data = """
            plain_key = "Hello"
            nested.key = "World"
        """.trimIndent()

        include("plain_key", "nested.key")
        exclude("nested:key")
    }

    should("flatten joined keys").config<CollectorSetup> {
        data = """
            plain_key = "Hello"
            "nested:key" = "World"
        """.trimIndent()

        include("plain_key", "nested.key")
        exclude("nested:key")
    }

    should("apply simple tags").config<CollectorSetup> {
        data = """
            tagged.key = "!"
        """.trimIndent()

        tags("tagged")
        include("key")
        exclude("tagged.key")
    }

    should("apply each tag at most once").config<CollectorSetup> {
        data = """
            tagged.tagged.key = "!"
        """.trimIndent()

        tags("tagged")
        include("tagged.key")
        exclude("tagged.tagged.key", "key")
    }

    should("apply ordered tags").config<CollectorSetup> {
        data = """
            ord.tag.key1 = 1
            tag.ord.key2 = 2
        """.trimIndent()

        tags("ord:tag")
        include("key1", "tag.ord.key2")
        exclude("key2")
    }

    should("apply full tags").config<CollectorSetup> {
        data = """
            partial-kinda.key1 = 1
            key2.part = 2
        """.trimIndent()

        tags("partial")
        include("partial-kinda.key1", "key2.part")
        exclude("key1", "key2")
    }

    should("prefer longer tags").config<CollectorSetup> {
        data = """
            double.nested.key1 = 1
            double.key2 = 2
        """.trimIndent()

        tags("double:nested", "double")
        include("key1", "key2")
        exclude("nested.key1")
    }

    should("not leave empty keys").config<CollectorSetup> {
        data = """
            very.tagged = 1
        """.trimIndent()

        tags("very", "tagged")
        include("tagged")
    }

    should("apply tags late").config<CollectorSetup> {
        data = """
            double.key.tag = 1
        """.trimIndent()

        tags("double", "tag")
        include("key")
    }
})

private class CollectorSetup : TestSetup {
    @Language("toml") lateinit var data: String

    val tags: MutableSet<String> = mutableSetOf()
    val shouldHave: MutableSet<String> = mutableSetOf()
    val shouldNotHave: MutableSet<String> = mutableSetOf()

    fun tags(vararg tags: String): Unit = this.tags.plusAssign(tags)
    fun include(vararg keys: String): Unit = shouldHave.plusAssign(keys)
    fun exclude(vararg keys: String): Unit = shouldNotHave.plusAssign(keys)

    override suspend fun TestScope.invoke() {
        val map = collect()

        println(TextColors.magenta(map.present()))
        if (shouldHave.isNotEmpty()) map.shouldContainKeys(*shouldHave.toTypedArray())
        if (shouldNotHave.isNotEmpty()) map.shouldNotContainKeys(*shouldNotHave.toTypedArray())
    }

    private fun collect(): Map<String, String> = buildMap {
        val collector = PropertiesCollector(tags, ::set)
        FromTomlConverter.convert(data).accept(collector, "")
    }
}
