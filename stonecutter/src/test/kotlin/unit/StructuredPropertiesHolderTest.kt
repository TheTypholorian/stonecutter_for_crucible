@file:Suppress("PublicApiImplicitType")

package unit

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stonecutter.data.deserialization.SCMap
import dev.kikugie.stonecutter.data.deserialization.SCPrimitive
import dev.kikugie.stonecutter.data.deserialization.TomlConverter
import dev.kikugie.stonecutter.data.property.StructuredPropertiesHolder
import dev.kikugie.stonecutter.data.property.StructuredPropertiesHolder.OverwritePolicy
import dev.kikugie.stonecutter.data.property.StructuredPropertiesHolder.OverwritePolicy.*
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
            holder("common.deps.value = 1", tags = listOf("common"))
                .get("deps.value") shouldBe SCPrimitive(1)
        }

        test("each tag applies at most once") {
            with(holder("tagged.tagged.key = 1", tags = listOf("tagged"))) {
                get("tagged.key") shouldBe SCPrimitive(1)
                get("key") shouldBe null
            }
        }

        test("ordered tag matches only a contiguous sequence") {
            with(holder("ord.tag.key1 = 1\ntag.ord.key2 = 2", tags = listOf("ord:tag"))) {
                get("key1") shouldBe SCPrimitive(1)
                get("tag.ord.key2") shouldBe SCPrimitive(2)
                get("key2") shouldBe null
            }
        }

        test("tag matches whole segments only") {
            with(holder("partial-kinda.key1 = 1\nkey2.part = 2", tags = listOf("partial"))) {
                get("partial-kinda.key1") shouldBe SCPrimitive(1)
                get("key1") shouldBe null
            }
        }

        test("the longest overlapping tag wins") {
            with(holder("double.nested.key1 = 1\ndouble.key2 = 2", tags = listOf("double:nested", "double"))) {
                get("key1") shouldBe SCPrimitive(1)
                get("key2") shouldBe SCPrimitive(2)
            }
        }

        test("tags never reduce a path to nothing") {
            holder("very.tagged = 1", tags = listOf("very", "tagged"))
                .get("tagged") shouldBe SCPrimitive(1)
        }

        test("tags apply at any position") {
            holder("common.extras.dependency.option = 1", tags = listOf("extras", "option"))
                .get("common.dependency") shouldBe SCPrimitive(1)
        }

        test("the deepest tagged match wins over a shallower default") {
            holder("common.value = 1\nvalue = 2", tags = listOf("common"))
                .get("value") shouldBe SCPrimitive(1)
        }
    }

    test("a dotted key name stays distinct from nested keys") {
        holder("a.b.c = \"nested\"", "\"a.b\".c = \"joined\"")
            .get("a.b.c") shouldBe SCPrimitive("nested")
    }

    test("a key name containing dots is matched as a whole segment") {
        with(holder("\"1.20.1\".loader = \"fabric\"")) {
            get("1.20.1.loader") shouldBe SCPrimitive("fabric")
            get("1.20.loader") shouldBe null
        }
    }

    test("get ignores composite structs") {
        holder("common.deps.a = 1", tags = listOf("common")).get("deps") shouldBe null
    }

    test("raw merges matching maps across tags") {
        holder("common.deps.a = 1", "fabric.deps.b = 2", tags = listOf("common", "fabric"))
            .raw("deps") shouldBe scmap(
            """
            a = 1
            b = 2
            """
        )
    }

    test("raw concatenates matching lists across tags") {
        holder("common.items = [1, 2]", "fabric.items = [3]", tags = listOf("common", "fabric"))
            .raw("items") shouldBe scmap("items = [1, 2, 3]")["items"]
    }

    test("raw merges nested maps and lists recursively across tags") {
        holder(
            "common.deps.a.x = 1\ncommon.deps.list = [1]",
            "fabric.deps.a.y = 2\nfabric.deps.list = [2]",
            tags = listOf("common", "fabric"),
        ).raw("deps") shouldBe scmap(
            """
            a.x = 1
            a.y = 2
            list = [1, 2]
            """
        )
    }

    test("get caches its result") {
        val holder = holder("x = 1")
        (holder.get("x") === holder.get("x")) shouldBe true
    }

    test("get returns nothing for a missing path") {
        holder("x = 1").get("y") shouldBe null
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
