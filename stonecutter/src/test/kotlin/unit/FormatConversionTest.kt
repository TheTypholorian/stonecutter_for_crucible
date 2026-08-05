@file:OptIn(StonecutterInternalAPI::class)
@file:Suppress("PublicApiImplicitType")

package unit

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stonecutter.StonecutterInternalAPI
import dev.kikugie.stonecutter.data.deserialization.JsonConverter
import dev.kikugie.stonecutter.data.deserialization.SCElement
import dev.kikugie.stonecutter.data.deserialization.SCMap
import dev.kikugie.stonecutter.data.deserialization.SCPrimitive
import dev.kikugie.stonecutter.data.deserialization.SCPrimitive.Kind
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.intellij.lang.annotations.Language

val FormatConversionTest by testSuite {
    testSuite("from json") {
        test("integer") {
            val element: SCMap = """
                {
                  it: 123
                }
            """.json()

            element["it"].shouldBeInstanceOf<SCPrimitive> {
                it.kind shouldBe Kind.Integer
                it.toInt() shouldBe 123
            }
        }

        test("float") {
            val element: SCMap = """
                {
                  it: 123.456
                }
            """.json()

            element["it"].shouldBeInstanceOf<SCPrimitive> {
                it.kind shouldBe Kind.Float
                it.toDouble() shouldBe 123.456
            }
        }

        test("float fractional") {
            val element: SCMap = """
                {
                  it: .456
                }
            """.json()

            element["it"].shouldBeInstanceOf<SCPrimitive> {
                it.kind shouldBe Kind.Float
                it.toDouble() shouldBe .456
            }
        }

        test("float exponent") {
            val element: SCMap = """
                {
                  it: 1e-123
                }
            """.json()

            element["it"].shouldBeInstanceOf<SCPrimitive> {
                it.kind shouldBe Kind.Float
                it.toDouble() shouldBe 1e-123
            }
        }

        test("integer hex") {
            val element: SCMap = """
                {
                  it: 0xdecaf
                }
            """.json()

            element["it"].shouldBeInstanceOf<SCPrimitive> {
                it.kind shouldBe Kind.Integer
                it.toInt() shouldBe 0xdecaf
            }
        }

        test("integer hex negative") {
            val element: SCMap = """
                {
                  it: -0xC0FFEE
                }
            """.json()

            element["it"].shouldBeInstanceOf<SCPrimitive> {
                it.kind shouldBe Kind.Integer
                it.toInt() shouldBe -0xC0FFEE
            }
        }

        test("float infinity") {
            val element: SCMap = """
                {
                  it: Infinity
                }
            """.json()

            element["it"].shouldBeInstanceOf<SCPrimitive> {
                it.kind shouldBe Kind.Float
                it.toDouble() shouldBe Double.POSITIVE_INFINITY
            }
        }

        test("float infinity negative") {
            val element: SCMap = """
                {
                  it: -Infinity
                }
            """.json()

            element["it"].shouldBeInstanceOf<SCPrimitive> {
                it.kind shouldBe Kind.Float
                it.toDouble() shouldBe Double.NEGATIVE_INFINITY
            }
        }

        test("float nan") {
            val element: SCMap = """
                {
                  it: NaN
                }
            """.json()

            element["it"].shouldBeInstanceOf<SCPrimitive> {
                it.kind shouldBe Kind.Float
                it.toString() shouldBe "NaN"
            }
        }
    }
}

private inline fun <reified T : SCElement> @receiver:Language("json5") String.json(): T {
    val element = JsonConverter.convert(this.trimIndent())
    return element.shouldBeInstanceOf<T>()
}
