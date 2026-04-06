@file:Suppress("ClassName", "FunctionName", "SimplifyBooleanWithConstants", "RedundantNullableReturnType", "unused")

package dev.kikugie.stonecutter.samples

import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import org.gradle.api.provider.ProviderFactory

private lateinit var providers: ProviderFactory
private fun property(name: String): Any? = throw UnsupportedOperationException()

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