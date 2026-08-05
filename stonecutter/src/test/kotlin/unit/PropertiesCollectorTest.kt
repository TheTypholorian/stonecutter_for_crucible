@file:OptIn(StonecutterInternalAPI::class)
@file:Suppress("PublicApiImplicitType")

package unit

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.commons.collections.present
import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.data.deserialization.TomlConverter
import dev.kikugie.stonecutter.settings.properties.PropertiesCollector
import io.kotest.matchers.maps.shouldContainKeys
import io.kotest.matchers.maps.shouldNotContainKeys
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language

val PropertiesCollectorTest by testSuite {
    test("flatten nested keys") {
        CollectorSetup().apply {
            data = """
                plain_key = "Hello"
                nested.key = "World"
            """.trimIndent()

            include("plain_key", "nested.key")
            exclude("nested:key")
        }.verify()
    }

    test("flatten joined keys") {
        CollectorSetup().apply {
            data = """
                plain_key = "Hello"
                "nested:key" = "World"
            """.trimIndent()

            include("plain_key", "nested.key")
            exclude("nested:key")
        }.verify()
    }

    test("apply simple tags") {
        CollectorSetup().apply {
            data = """
                tagged.key = "!"
            """.trimIndent()

            tags("tagged")
            include("key")
            exclude("tagged.key")
        }.verify()
    }

    test("apply each tag at most once") {
        CollectorSetup().apply {
            data = """
                tagged.tagged.key = "!"
            """.trimIndent()

            tags("tagged")
            include("tagged.key")
            exclude("tagged.tagged.key", "key")
        }.verify()
    }

    test("apply ordered tags") {
        CollectorSetup().apply {
            data = """
                ord.tag.key1 = 1
                tag.ord.key2 = 2
            """.trimIndent()

            tags("ord:tag")
            include("key1", "tag.ord.key2")
            exclude("key2")
        }.verify()
    }

    test("apply full tags") {
        CollectorSetup().apply {
            data = """
                partial-kinda.key1 = 1
                key2.part = 2
            """.trimIndent()

            tags("partial")
            include("partial-kinda.key1", "key2.part")
            exclude("key1", "key2")
        }.verify()
    }

    test("prefer longer tags") {
        CollectorSetup().apply {
            data = """
                double.nested.key1 = 1
                double.key2 = 2
            """.trimIndent()

            tags("double:nested", "double")
            include("key1", "key2")
            exclude("nested.key1")
        }.verify()
    }

    test("not leave empty keys") {
        CollectorSetup().apply {
            data = """
                very.tagged = 1
            """.trimIndent()

            tags("very", "tagged")
            include("tagged")
        }.verify()
    }

    test("apply tags late") {
        CollectorSetup().apply {
            data = """
                double.key.tag = 1
            """.trimIndent()

            tags("double", "tag")
            include("key")
        }.verify()
    }

    // https://codeberg.org/stonecutter/stonecutter/issues/50
    // Values are defined out of order here because it has to be *deepmost*, not *latest* — regardless of the format.
    test("use deepmost value") {
        CollectorSetup().apply {
            data = """
                "1.20.1".setting = "specific"
                setting = "global"
            """.trimIndent()

            tags("1.20.1")
            include("setting")
            exclude("1.20.1.setting")
            result {
                get("setting") shouldBe "specific"
            }
        }.verify()
    }
}

private class CollectorSetup {
    @Language("toml") lateinit var data: String
    private var check: (Map<String, String>) -> Unit = {}

    val tags: MutableSet<String> = mutableSetOf()
    val shouldHave: MutableSet<String> = mutableSetOf()
    val shouldNotHave: MutableSet<String> = mutableSetOf()

    fun tags(vararg tags: String): Unit = this.tags.plusAssign(tags)
    fun include(vararg keys: String): Unit = shouldHave.plusAssign(keys)
    fun exclude(vararg keys: String): Unit = shouldNotHave.plusAssign(keys)
    fun result(action: Map<String, String>.() -> Unit) { check = action }

    fun verify() {
        val map = collect()

        println(map.present())
        if (shouldHave.isNotEmpty()) map.shouldContainKeys(*shouldHave.toTypedArray())
        if (shouldNotHave.isNotEmpty()) map.shouldNotContainKeys(*shouldNotHave.toTypedArray())
        check(map)
    }

    private fun collect(): Map<String, String> = buildMap {
        val collector = PropertiesCollector(tags) { put(key.replace(':', '.'), primitive.toString()) }
        TomlConverter.convert(data).accept(collector, "")
    }
}
