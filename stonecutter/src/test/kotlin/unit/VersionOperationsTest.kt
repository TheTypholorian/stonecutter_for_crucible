@file:Suppress("PublicApiImplicitType")

package unit

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.semver.data.StringVersion
import dev.kikugie.semver.impl.VersionParsingException
import dev.kikugie.stonecutter.data.version.LenientOperations
import dev.kikugie.stonecutter.data.version.SemanticOperations
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

private fun wildcard(vararg components: Int) =
    SemanticVersion(components.toList(), SemanticVersion.PRE_RELEASE_WILDCARD)

val VersionOperationsTest by testSuite {
    testSuite("parsing") {
        test("reads both version kinds dynamically") {
            LenientOperations.parse("1.21.1").shouldBeInstanceOf<SemanticVersion>()
            LenientOperations.parse("release").shouldBeInstanceOf<StringVersion>()
            SemanticOperations.parse("1.21.1").components shouldBe listOf(1, 21, 1)
        }

        test("the semantic operations reject a string version") {
            shouldThrow<IllegalArgumentException> { SemanticOperations.parse("release") }
        }

        test("a failure keeps the parser exception and quotes the input") {
            val failure = shouldThrow<IllegalArgumentException> { LenientOperations.parse("1.2.x") }
            failure.suppressed.single().shouldBeInstanceOf<VersionParsingException>()
            failure.message!! shouldContain "| 1.2.x"
            failure.message!! shouldContain "^"
        }

        test("the try- form reports the parser exception instead of throwing") {
            LenientOperations.tryParse("1.21.1").getOrThrow() shouldBe SemanticVersion(1, 21, 1)
            LenientOperations.tryParse("1.2.x").exceptionOrNull().shouldBeInstanceOf<VersionParsingException>()
        }
    }

    // `-*` marks the start of a release line, which only a predicate's target may do.
    testSuite("pre-release wildcard parsing") {
        test("a bare version refuses it") {
            shouldThrow<IllegalArgumentException> { LenientOperations.parse("1.21-*") }
            shouldThrow<IllegalArgumentException> { SemanticOperations.parse("1.21-*") }
            LenientOperations.tryParse("1.21-*").exceptionOrNull().shouldBeInstanceOf<VersionParsingException>()
        }

        test("a predicate target accepts it") {
            LenientOperations.parseAsPredicate("1.21-*") shouldBe wildcard(1, 21)
            SemanticOperations.parseAsPredicate("1.21-*") shouldBe wildcard(1, 21)
            LenientOperations.tryParseAsPredicate("1.21-*").getOrThrow() shouldBe wildcard(1, 21)
        }

        // Only the target is read, so the operator that would carry it is still out of place.
        test("an operator is not part of a target") {
            shouldThrow<IllegalArgumentException> { LenientOperations.parseAsPredicate(">=1.21-*") }
            LenientOperations.tryParseAsPredicate(">=1.21-*")
                .exceptionOrNull().shouldBeInstanceOf<VersionParsingException>()
        }

        test("input without a wildcard reads the same either way") {
            LenientOperations.parseAsPredicate("1.21.1") shouldBe LenientOperations.parse("1.21.1")
        }

        // The two forms disagree on this one input and are keyed by it, so they need separate
        // caches - sharing one would let whichever ran first answer for the other.
        test("the two forms don't answer for each other through the cache") {
            LenientOperations.parseAsPredicate("3.4-*") shouldBe wildcard(3, 4)
            shouldThrow<IllegalArgumentException> { LenientOperations.parse("3.4-*") }

            shouldThrow<IllegalArgumentException> { LenientOperations.parse("5.6-*") }
            LenientOperations.parseAsPredicate("5.6-*") shouldBe wildcard(5, 6)
        }
    }

    testSuite("evaluation") {
        test("takes predicates joined in one string or spread over several") {
            val version = LenientOperations.parse("1.21.1")
            LenientOperations.eval(version, ">1.20 <1.22") shouldBe true
            LenientOperations.eval(version, ">1.20", "<1.22") shouldBe true
            LenientOperations.eval(version, ">1.20", ">=1.22") shouldBe false
        }

        test("takes the version as text too") {
            LenientOperations.eval("1.21.1", ">1.20 <1.22") shouldBe true
            LenientOperations.tryEval("1.21.1", ">=1.22").getOrThrow() shouldBe false
        }

        test("a wildcard bound reaches into the pre-releases of its line") {
            val version = LenientOperations.parse("1.21-alpha.1")
            LenientOperations.eval(version, ">=1.21-*") shouldBe true
            LenientOperations.eval(version, ">=1.21") shouldBe false
            LenientOperations.eval(version, "<1.21-*") shouldBe false
            SemanticOperations.eval(version, ">=1.21-*") shouldBe true
        }

        test("a wildcard range spans exactly one release line") {
            LenientOperations.eval("1.20-beta", ">=1.20-*", "<1.21-*") shouldBe true
            LenientOperations.eval("1.20.6", ">=1.20-*", "<1.21-*") shouldBe true
            LenientOperations.eval("1.21-alpha.1", ">=1.20-*", "<1.21-*") shouldBe false
        }

        test("an unreadable predicate fails rather than being skipped") {
            shouldThrow<IllegalArgumentException> { LenientOperations.eval("1.21.1", ">1.20 <>") }
            LenientOperations.tryEval("1.21.1", ">1.20 <>")
                .exceptionOrNull().shouldBeInstanceOf<VersionParsingException>()
        }

        // Offsets are reported into the predicates concatenated, but the message joins them
        // with ", ", so the pointer has to move over every separator that precedes the failure.
        test("the failure points into the joined predicates") {
            val failure = shouldThrow<IllegalArgumentException> { LenientOperations.eval("1.21.1", ">1.20", "^release") }
            failure.message!!.lines().drop(1) shouldBe listOf("| >1.20, ^release", "| " + " ".repeat(7) + "^")
        }

        test("predicates already in one string need no shift") {
            val failure = shouldThrow<IllegalArgumentException> { LenientOperations.eval("1.21.1", ">1.20 ^release") }
            failure.message!!.lines().drop(1) shouldBe listOf("| >1.20 ^release", "| " + " ".repeat(6) + "^")
        }
    }

    testSuite("comparison") {
        test("orders two version strings") {
            (LenientOperations.compare("1.2.0", "1.10.0") < 0) shouldBe true
            LenientOperations.compare("1.2", "1.2.0") shouldBe 0
            (LenientOperations.tryCompare("2.0", "1.0").getOrThrow() > 0) shouldBe true
        }

        // Ordering reads its operands as predicate targets, so a wildcard bound can be ordered.
        test("orders a wildcard at the start of its line") {
            (LenientOperations.compare("1.21-*", "1.21-alpha") < 0) shouldBe true
            (LenientOperations.compare("1.21-*", "1.21") < 0) shouldBe true
            (LenientOperations.compare("1.21-*", "1.20.6") > 0) shouldBe true
            LenientOperations.tryCompare("1.21-*", "1.21-*").getOrThrow() shouldBe 0
        }

        test("reports an unreadable operand") {
            shouldThrow<IllegalArgumentException> { LenientOperations.compare("1.2.x", "1.0") }
            LenientOperations.tryCompare("1.0", "1.2.x")
                .exceptionOrNull().shouldBeInstanceOf<VersionParsingException>()
        }
    }
}
