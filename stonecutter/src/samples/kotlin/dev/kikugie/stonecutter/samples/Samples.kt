@file:Suppress("ClassName", "FunctionName", "SimplifyBooleanWithConstants", "RedundantNullableReturnType", "unused")
@file:OptIn(StonecutterExperimentalAPI::class)

package dev.kikugie.stonecutter.samples

import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import dev.kikugie.stonecutter.data.deserialization.SCElement
import org.gradle.api.provider.ProviderFactory
import org.intellij.lang.annotations.Language

private lateinit var providers: ProviderFactory
private fun property(name: String): Any = throw UnsupportedOperationException()
private fun hasProperty(name: String): Boolean = throw UnsupportedOperationException()
private fun findProperty(name: String): Any? = throw UnsupportedOperationException()

private object constants {
    private lateinit var sc: StonecutterBuildExtension
    private lateinit var stonecutter: StonecutterBuildExtension

    private inline fun stonecutter(action: StonecutterBuildExtension.() -> Unit) = Unit

    fun match_vararg() {
        val (version, loader) = sc.current.project.split('-', limit = 2)

        stonecutter {
            constants.match(loader, "fabric", "neoforge", "forge")
        }
    }

    fun match_iter() {
        val (version, loader) = sc.current.project.split('-', limit = 2)
        val loaders = property("mod_loaders").toString().split(',')

        stonecutter {
            constants.match(loader, loaders)
        }
    }
}

private object properties {
    private lateinit var sc: StonecutterBuildExtension

    fun load_cutting() {
        // # stonecutter.properties.toml
        // my_property = "top-level"
        // "1.21.1:my_property1" = "specific
        //
        // ["1.21.1"]
        // my_property2 = "specific too"

        // # stonecutter.gradle.kts
        assert(property("my_property") == "top-level")

        // # build.gradle.kts
        assert(sc.current.project == "1.21.1")
        assert(property("my_property1") == "specific")
        assert(property("my_property2") == "specific too")
    }

    fun load_flattening() {
        // # stonecutter.properties.toml
        // [category]
        // nested.key = "Hello World!"

        // # *.gradle.kts
        assert(property("category.nested.key") == "Hello World!")
    }

    fun tags() {
        // # stonecutter.properties.toml
        // [category]
        // my_property = "Hello World!"

        // # *.gradle.kts
        assert(!hasProperty("my_property"))
        assert(property("category.my_property") == "Hello World!")

        sc.properties.tags("category")
        assert(property("my_property") == "Hello World!")
    }

    fun ordered_tags() {
        // # stonecutter.properties.toml
        // [ordered.category]
        // my_property1 = "Hello World #1!"
        //
        // [category.ordered]
        // my_property3 = "Hello World #3!"
        //
        // [unordered.category]
        // my_property3 = "Hello World #3!"

        // # *.gradle.kts
        sc.properties.tags("ordered:category", "category", "unordered")
        assert(hasProperty("my_property1"))
        assert(!hasProperty("my_property2"))
        assert(hasProperty("my_property3"))
    }

    fun raw() {
        // # stonecutter.properties.toml
        // key.items = [1, 2, 3]

        val element: SCElement = sc.properties.raw("key", "items")
        val items: List<Int> = element.asList().map { it.asPrimitive().toInt() }
    }
}
