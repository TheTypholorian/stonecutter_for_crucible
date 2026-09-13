@file:Suppress("PublicApiImplicitType")

package unit

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stonecutter.data.ParsedVersion
import dev.kikugie.stonecutter.data.StonecutterProject
import dev.kikugie.stonecutter.data.version.LenientOperations
import dev.kikugie.stonecutter.util.contains
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.util.Optional

private fun version(value: String) = ParsedVersion(value)

val ParsedVersionTest by testSuite {
    testSuite("comparison") {
        test("accepts the three documented target types") {
            val one = version("1.2.0")
            (one compareTo version("1.3.0") < 0) shouldBe true
            (one compareTo version("1.3.0").value < 0) shouldBe true
            (one compareTo "1.3.0" < 0) shouldBe true
        }

        test("rejects what can't name a version") {
            shouldThrow<IllegalArgumentException> { version("1.2.0") compareTo File("1.3.0") }
            shouldThrow<IllegalArgumentException> { version("1.2.0") compareTo listOf(1, 2) }
            shouldThrow<IllegalArgumentException> { version("1.2.0") compareTo "" }
        }

        test("orders a homogeneous list") {
            listOf(version("1.10.0"), version("1.2.0"), version("2.0.0"))
                .sorted()
                .map(ParsedVersion::toString) shouldBe listOf("1.2.0", "1.10.0", "2.0.0")
        }

        test("a pre-release precedes its release") {
            (version("1.0.0-beta.1") compareTo "1.0.0" < 0) shouldBe true
        }

        test("orders string and semantic versions symmetrically") {
            (version("1.0") compareTo version("release") < 0) shouldBe true
            (version("release") compareTo version("1.0") > 0) shouldBe true
            version("1.0") shouldNotBe version("release")
            version("release") shouldNotBe version("1.0")
        }
    }

    testSuite("coercion") {
        test("reads any character sequence") {
            // Stands in for a Groovy GString, which is a CharSequence but not a String.
            (version("1.2.0") compareTo StringBuilder("1.3.0") < 0) shouldBe true
        }

        test("reads numbers") {
            (version("1.2.0") compareTo 2 < 0) shouldBe true
            (version("1.2.0") compareTo 1.3 < 0) shouldBe true
            version("1.2") shouldBe 1.20
        }

        test("reads a project descriptor") {
            (version("1.2.0") compareTo StonecutterProject("v1_3_0", "1.3.0") < 0) shouldBe true
        }

        test("unwraps containers, including nested ones") {
            version("1.2.0") shouldBe Optional.of("1.2.0")
            version("1.2.0") shouldBe Result.success("1.2.0")
            version("1.2.0") shouldBe lazy { "1.2.0" }
            version("1.2.0") shouldBe Optional.of(lazy { version("1.2.0") })
        }

        test("an absent value is unequal rather than an error") {
            version("1.2.0") shouldNotBe Optional.empty<String>()
            version("1.2.0") shouldNotBe null
            version("1.2.0") shouldNotBe File("1.2.0")
        }
    }

    testSuite("predicates in comparisons") {
        test("equality evaluates predicate text") {
            version("1.21") shouldBe ">=1.20"
            version("1.21") shouldBe ">1.20 <1.22"
            version("1.21") shouldNotBe "<1.20"
        }

        test("ordering discards the predicate operator") {
            val one = version("1.21")
            (one compareTo ">=1.20") shouldBe (one compareTo "1.20")
            (one compareTo "~1.21") shouldBe 0
        }

        test("ordering rejects several predicates at once") {
            shouldThrow<IllegalArgumentException> { version("1.21") compareTo ">1.20 <1.22" }
        }
    }

    testSuite("equality") {
        test("equal versions share a hash code") {
            val a = version("1.2.0")
            val b = version("1.2.0")
            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
        }

        test("usable as a map key") {
            val map = mapOf(version("1.2.0") to "hit")
            map[version("1.2.0")] shouldBe "hit"
            setOf(version("1.2.0"), version("1.2.0")).size shouldBe 1
        }

        // `equals` accepts every operand a comparison does.
        test("equal to the types compareTo accepts") {
            version("1.2.0") shouldBe "1.2.0"
            version("1.2.0") shouldBe version("1.2.0").value
            version("1.2.0") shouldNotBe "1.3.0"
        }

        // Ordering decides equality, so anything carrying no precedence has to be invisible to
        // both `equals` and `hashCode` - otherwise these pairs would occupy two slots in a set.
        test("versions equal by precedence are equal objects") {
            val equivalent = listOf("1", "1.0", "1.0.0", "1.0.0.0", "1.0.0+build.7")
                .map(::version)

            for (other in equivalent) {
                equivalent.first() shouldBe other
                equivalent.first().hashCode() shouldBe other.hashCode()
            }

            equivalent.toSet().size shouldBe 1
            mapOf(version("1.0") to "hit")[version("1.0.0+build.7")] shouldBe "hit"
        }

        test("pre-release carries precedence and is kept apart") {
            version("1.0.0-beta") shouldNotBe version("1.0.0")
            version("1.0.0-beta") shouldBe version("1.0.0-beta+build.7")
            version("1.0.0-beta").hashCode() shouldBe version("1.0.0-beta+build.7").hashCode()
        }

        test("distinct versions stay distinct") {
            version("1.0.1") shouldNotBe version("1.0.0")
            version("2.0.0") shouldNotBe version("1.0.0")
        }

        // `==` doesn't compile against a String - both types are final and unrelated,
        // so Kotlin rejects the operator before `equals` is ever reached.
        test("the infix form stands in for the equality operator") {
            (version("1.0") eq "1") shouldBe true
            (version("1.0") eq "1.0.0") shouldBe true
            (version("1.0") eq ">=1") shouldBe true
            (version("1.0") eq "1.0.1") shouldBe false
            (version("1.0") eq null) shouldBe false
            (version("1.0") eq File("1.0")) shouldBe false
        }
    }

    testSuite("ranges") {
        test("checks a range of version literals through the imported operator") {
            (version("1.21") in "1.20".."1.22") shouldBe true
            (version("1.23") in "1.20".."1.22") shouldBe false
            (version("1.21") in "1.20"..<"1.22") shouldBe true
            (version("1.22") in "1.20"..<"1.22") shouldBe false
        }

        // The same operator is a member of `VersionOperations`, so it needs no import
        // inside the DSL extensions that implement it.
        test("checks a range of version literals through the DSL receiver") {
            with(LenientOperations) {
                (version("1.21") in "1.20".."1.22") shouldBe true
                (version("1.22") in "1.20"..<"1.22") shouldBe false
            }
        }

        test("the infix form takes the same bounds") {
            (version("1.21") within "1.20".."1.22") shouldBe true
            (version("1.19") within "1.20".."1.22") shouldBe false
        }

        test("the infix form excludes an open upper bound") {
            (version("1.21") within "1.20"..<"1.22") shouldBe true
            (version("1.22") within "1.20"..<"1.22") shouldBe false
            (version("1.22") within "1.20".."1.22") shouldBe true
        }

        test("builds a range from a version") {
            (version("1.21") in version("1.20").."1.22") shouldBe true
            (version("1.22") in version("1.20").."1.22") shouldBe true
            (version("1.21") in version("1.20")..<"1.22") shouldBe true
            (version("1.22") in version("1.20")..<"1.22") shouldBe false
        }

        test("range bounds are interpreted like any operand") {
            (version("1.21") within version("1").."2") shouldBe true
            (version("3") within version("1").."2") shouldBe false
        }
    }

    testSuite("factories") {
        test("wraps anything a comparison accepts") {
            ParsedVersion(1.2).toString() shouldBe "1.2"
            ParsedVersion(StringBuilder("1.2.0")).toString() shouldBe "1.2.0"
            shouldThrow<IllegalArgumentException> { ParsedVersion(">=1.20") }
        }

        test("the null-returning form swallows the failure") {
            ParsedVersion.parseOrNull("1.2.0").shouldNotBeNull()
            ParsedVersion.parseOrNull(">=1.20") shouldBe null
            ParsedVersion.parseOrNull(File("1.2.0")) shouldBe null
            ParsedVersion.parseOrNull(null) shouldBe null
        }
    }

    testSuite("text") {
        test("reads as the original version string") {
            version("1.21.1").toString() shouldBe "1.21.1"
        }

        test("keeps pre-release and build metadata") {
            version("1.0.0-beta.2+build.7").toString() shouldBe "1.0.0-beta.2+build.7"
        }

        // The wrapper is no longer a CharSequence, so text operations go through `toString`.
        test("normalizes nothing on the way out") {
            version("1").toString() shouldBe "1"
            version("1.0.0.0").toString() shouldBe "1.0.0.0"
            version("release").toString() shouldBe "release"
        }
    }

    testSuite("predicates") {
        test("matches space-separated predicates") {
            val it = version("1.21.1")
            (it matches ">1.20 <1.22") shouldBe true
            (it matches ">=1.22") shouldBe false
        }

        test("matches several arguments") {
            version("1.21.1").matches(">1.20", "<1.22") shouldBe true
            version("1.21.1")(">1.20", "<1.22") shouldBe true
            version("1.21.1")(">=1.22") shouldBe false
        }

        test("a short core satisfies the range operators") {
            (version("1.0.0") matches "~1") shouldBe true
            (version("1.2.0") matches "^1") shouldBe true
        }
    }
}
