@file:Suppress("PublicApiImplicitType")

package unit

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stonecutter.data.deserialization.SCElement
import dev.kikugie.stonecutter.data.deserialization.SCMap
import dev.kikugie.stonecutter.data.deserialization.SCPrimitive
import dev.kikugie.stonecutter.data.deserialization.TomlConverter
import dev.kikugie.stonecutter.data.property.StructuredPropertiesHolder
import dev.kikugie.stonecutter.data.property.StructuredPropertiesHolder.OverwritePolicy
import dev.kikugie.stonecutter.data.property.StructuredPropertiesHolder.OverwritePolicy.KEEP
import dev.kikugie.stonecutter.data.property.StructuredPropertiesHolder.OverwritePolicy.MERGE
import dev.kikugie.stonecutter.data.property.StructuredPropertiesHolder.OverwritePolicy.WRITE
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import java.io.File

val StructuredPropertiesHolderTest by testSuite {
    testSuite("merging") {
        test("union disjoint keys") {
            holder("x = 1", "y = 2").all() shouldBe scmap(
                """
                x = 1
                y = 2
                """
            )
        }

        test("merge recurses into shared maps") {
            holder("a.x = 1\nb = 2", "a.y = 9\nc = 3").all() shouldBe scmap(
                """
                a.x = 1
                a.y = 9
                b = 2
                c = 3
                """
            )
        }

        test("merge concatenates lists") {
            holder("x = [1, 2]", "x = [3]").all() shouldBe scmap("x = [1, 2, 3]")
        }

        test("merge overwrites primitives with the last value") {
            holder("x = 1", "x = 2").all() shouldBe scmap("x = 2")
        }

        test("merge writes incoming on a type mismatch") {
            holder("a.x = 1", "a = 5").all() shouldBe scmap("a = 5")
            holder("a = 5", "a.x = 1").all() shouldBe scmap("a.x = 1")
        }

        test("write replaces shared values wholesale") {
            holder("a.x = 1\nb = 2", "a.y = 9\nc = 3", policy = WRITE).all() shouldBe scmap(
                """
                a.y = 9
                b = 2
                c = 3
                """
            )
        }

        test("write replaces shared lists instead of concatenating") {
            holder("x = [1, 2]", "x = [3]", policy = WRITE).all() shouldBe scmap("x = [3]")
        }

        test("keep retains the first shared value but adds new keys") {
            holder("a.x = 1\nb = 2", "a.y = 9\nc = 3", policy = KEEP).all() shouldBe scmap(
                """
                a.x = 1
                b = 2
                c = 3
                """
            )
        }

        test("keep retains the first shared list") {
            holder("x = [1, 2]", "x = [3]", policy = KEEP).all() shouldBe scmap("x = [1, 2]")
        }

        test("the same file is loaded only once") {
            val file = File("stonecutter.properties.toml")
            StructuredPropertiesHolder().apply {
                load(file) { scmap("x = 1") } shouldBe true
                load(file) { scmap("x = 2") } shouldBe false
            }.all() shouldBe scmap("x = 1")
        }
    }

    testSuite("tagging") {
        test("tag removes a matching segment") {
            holder("common.deps.value = 1", tags = listOf("common")).all() shouldBe scmap("deps.value = 1")
        }

        test("each tag applies at most once") {
            holder("tagged.tagged.key = 1", tags = listOf("tagged")).all() shouldBe scmap("tagged.key = 1")
        }

        test("ordered tag matches only a contiguous sequence") {
            holder("ord.tag.key1 = 1\ntag.ord.key2 = 2", tags = listOf("ord:tag")).all() shouldBe scmap(
                """
                key1 = 1
                tag.ord.key2 = 2
                """
            )
        }

        test("tag matches whole segments only") {
            holder("partial-kinda.key1 = 1\nkey2.part = 2", tags = listOf("partial")).all() shouldBe scmap(
                """
                partial-kinda.key1 = 1
                key2.part = 2
                """
            )
        }

        test("the longest overlapping tag wins") {
            holder("double.nested.key1 = 1\ndouble.key2 = 2", tags = listOf("double:nested", "double")).all() shouldBe scmap(
                """
                key1 = 1
                key2 = 2
                """
            )
        }

        test("tags never reduce a path to nothing") {
            holder("very.tagged = 1", tags = listOf("very", "tagged")).all() shouldBe scmap("tagged = 1")
        }

        test("tags apply at any position") {
            holder("common.extras.dependency.option = 1", tags = listOf("extras", "option")).all() shouldBe scmap(
                "common.dependency = 1"
            )
        }

        test("tag collisions follow the merge policy") {
            holder("common.value = 1\nvalue = 2", tags = listOf("common")).all() shouldBe scmap("value = 2")
            holder("common.value = 1\nvalue = 2", policy = KEEP, tags = listOf("common")).all() shouldBe scmap("value = 1")
        }
    }

    test("get returns every match for an ambiguous dotted path") {
        val holder = holder("a.b.c = \"nested\"", "\"a.b\".c = \"joined\"")
        holder.get("a.b.c").primitives() shouldBe listOf("nested", "joined")
    }

    test("get caches its result") {
        val holder = holder("x = 1")
        (holder.get("x") === holder.get("x")) shouldBe true
    }

    test("get returns nothing for a missing path") {
        holder("x = 1").get("y").primitives() shouldBe emptyList()
    }
}

private fun scmap(@Language("toml") text: String): SCMap =
    TomlConverter.convert(text.trimIndent()) as SCMap

private fun holder(
    @Language("toml") vararg structs: String,
    policy: OverwritePolicy = MERGE,
    tags: List<String> = emptyList(),
): StructuredPropertiesHolder = StructuredPropertiesHolder().apply {
    overwrite(policy)
    for (struct in structs) load(null) { scmap(struct) }
    if (tags.isNotEmpty()) tags(tags)
}

private fun List<SCElement>.primitives(): List<Any> = map { (it as SCPrimitive).content }
