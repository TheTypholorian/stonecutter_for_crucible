@file:Suppress("PublicApiImplicitType")
@file:OptIn(ExperimentalPathApi::class)

package integration

import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.paths.shouldNotExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import read
import runGradle
import tempDirFixture
import kotlin.io.path.*

private const val GENERATE: String = ":2:stonecutterGenerate"
private const val BUILD: String = "versions/2/build"
private const val GENERATED: String = "$BUILD/generated/stonecutter/main"
private const val CLASSES: String = "$BUILD/classes/java/main"

val SourceRootsTest by testSuite {
    tempDirFixture() asParameterForEach {
        integrationTest("process custom source directories") {
            it.resolve("versions/2/source/Override.java").apply { createParentDirectories() }
                .writeText("public class Override { int local; }")
            runGradle(it, ":2:classes")

            it read "$GENERATED/source/Custom.java" shouldContain "//String value"
            it.resolve("$GENERATED/source/Override.java").shouldNotExist()
            it.resolve("$CLASSES/Custom.class").shouldExist()
            it.resolve("$CLASSES/Override.class").shouldExist()

            runGradle(it, ":2:stonecutterMerge")
            it read "source/Custom.java" shouldContain "//String value"
            it read "source/Override.java" shouldBe "public class Override {}\n"
        }

        integrationTest("suffix duplicate directory names") {
            runGradle(it, ":2:classes")

            it.resolve("$GENERATED/java/Main.java").shouldExist()
            it read "$GENERATED/java-f27a65c1/Other.java" shouldContain "//String value"
            it.resolve("$GENERATED/lib-056de7d7/First.java").shouldExist()
            it.resolve("$GENERATED/lib-057bff58/Second.java").shouldExist()
            it.resolve("$GENERATED/lib").shouldNotExist()
            for (name in listOf("Main", "Other", "First", "Second"))
                it.resolve("$CLASSES/$name.class").shouldExist()
        }

        integrationTest("keep suffix across locations") {
            val entries = it.listDirectoryEntries()
            val locations = listOf(it.resolve("first"), it.resolve("nested/location/second"))
            for (location in locations.onEach { it.createDirectories() }) for (entry in entries)
                entry.copyToRecursively(location.resolve(entry.name), followLinks = false, overwrite = true)

            for (location in locations) {
                runGradle(location, GENERATE)
                location.resolve(GENERATED).listDirectoryEntries().map { it.name }.toSet() shouldBe setOf("java", "java-f27a65c1")
            }
        }

        integrationTest("ignore build directory sources") {
            it.resolve("versions/2/output/excluded/Local.java").apply { createParentDirectories() }
                .writeText("public class Local {}")
            runGradle(it, ":2:classes")

            val build = "versions/2/output"
            it.resolve("$build/generated/stonecutter/main").listDirectoryEntries().map { it.name } shouldBe listOf("java")
            it.resolve("$build/generated/stonecutter/main/java/Main.java").shouldExist()
            it.resolve("$build/classes/java/main/Local.class").shouldExist()
            it.resolve("$build/classes/java/main/Shared.class").shouldNotExist()
            it.resolve(BUILD).shouldNotExist()
        }

        integrationTest("ignore external sources") {
            runGradle(it, ":2:classes")

            it.resolve("$GENERATED/java/Main.java").shouldExist()
            it.resolve("$GENERATED/external").shouldNotExist()
            it.resolve("$CLASSES/External.class").shouldExist()
            it read "external/External.java" shouldContain "    String value"
        }
    }
}
