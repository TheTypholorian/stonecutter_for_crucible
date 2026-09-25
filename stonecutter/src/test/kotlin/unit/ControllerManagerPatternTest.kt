@file:Suppress("PublicApiImplicitType")

package unit

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stonecutter.controller.StonecutterControllerManager
import dev.kikugie.stonecutter.controller.StonecutterControllerManager.Groovy
import dev.kikugie.stonecutter.controller.StonecutterControllerManager.Kotlin
import io.kotest.matchers.shouldBe

val ControllerManagerPatternTest by testSuite {
    testSuite("groovy") {
        for (spec in listOf(
            ReplaceSpec(Groovy, """stonecutter.active("1.0")""", "2.0", """stonecutter.active("2.0")"""),
            ReplaceSpec(Groovy, """stonecutter.active ('1.0')""", "2.0", """stonecutter.active ('2.0')"""),
            ReplaceSpec(Groovy, """stonecutter . active "1.0"""", "2.0", """stonecutter . active "2.0""""),
            ReplaceSpec(Groovy, """stonecutter .active '1.0'""", "2.0", """stonecutter .active '2.0'"""),
            ReplaceSpec(Groovy, """sc.active ( "1.0" )""", "2.0", """sc.active ( "2.0" )"""),
            ReplaceSpec(Groovy, """sc.active ('1.0')""", "2.0", """sc.active ('2.0')"""),
            ReplaceSpec(Groovy, """sc . active "1.0"""", "2.0", """sc . active "2.0""""),
            ReplaceSpec(Groovy, """sc .active '1.0'""", "2.0", """sc .active '2.0'"""),
        )) test(spec.toString()) { spec.run() shouldBe spec.output }
    }

    testSuite("kotlin") {
        for (spec in listOf(
            ReplaceSpec(Kotlin, """stonecutter . active ("1.0")""", "2.0", """stonecutter . active ("2.0")"""),
            ReplaceSpec(Kotlin, """stonecutter active "1.0"""", "2.0", """stonecutter active "2.0""""),
            ReplaceSpec(Kotlin, """sc.active ( "1.0" )""", "2.0", """sc.active ( "2.0" )"""),
            ReplaceSpec(Kotlin, """sc active "1.0"""", "2.0", """sc active "2.0""""),
            ReplaceSpec(Kotlin, """stonecutter active "a"""", "1.20", """stonecutter active "1.20""""),
            ReplaceSpec(Kotlin, "val a = 1\nstonecutter active \"a\"\nprintln(a)", "b", "val a = 1\nstonecutter active \"b\"\nprintln(a)"),
        )) test(spec.toString()) { spec.run() shouldBe spec.output }
    }
}

private data class ReplaceSpec(val controller: StonecutterControllerManager, val input: String, val template: String, val output: String) {
    override fun toString(): String = "{$input} ~ '$template'"
    fun run() = controller.replace(input, template)
}
