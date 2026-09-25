@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import org.gradle.testkit.runner.GradleRunner
import io.kotest.matchers.paths.shouldNotExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import read
import runGradle
import runGradleIncremental
import shouldFailBuild
import tempDirFixture
import java.io.File
import java.nio.file.attribute.FileTime
import kotlin.io.path.*

private const val GENERATE: String = ":2:stonecutterGenerate"
private const val GENERATED: String = "versions/2/build/generated/stonecutter/main"
private val PLUGIN_CLASSPATH: String = GradleRunner.create().withPluginClasspath()
    .pluginClasspath.joinToString(File.pathSeparator)

val GenerateTaskTest by testSuite {
    tempDirFixture() asParameterForEach {
        integrationTest("reject non-UTF-8 files") {
            it.resolve("src/main/java/Invalid.java")
                .writeText("public class Invalid { String name = \"café\"; }", Charsets.ISO_8859_1)

            val failure = shouldFailBuild { runGradle(it, GENERATE) }
            failure.message shouldContain "is not valid UTF-8"
            it.resolve("$GENERATED/java/Invalid.java").shouldNotExist()
        }

        integrationTest("remove output of deleted file") {
            val binary = it.resolve("src/main/resources/image.png")
            runGradleIncremental(it, GENERATE)
            it.resolve("$GENERATED/resources/image.png").readBytes() shouldBe binary.readBytes()

            binary.deleteExisting()
            runGradleIncremental(it, GENERATE)
            it.resolve("$GENERATED/resources/image.png").shouldNotExist()
            it read "$GENERATED/java/Example.java" shouldContain "//String value"
        }

        integrationTest("reprocess only changed files") {
            runGradleIncremental(it, GENERATE)
            val untouched = it.resolve("$GENERATED/java/Second.java")
            val marker = FileTime.fromMillis(946684800000)
            untouched.setLastModifiedTime(marker)

            val changed = it.resolve("src/main/java/First.java")
            changed.writeText(changed.readText().replace("}", "    //? if <2\n    int added = 1;\n}"))
            val output = runGradleIncremental(it, GENERATE, "--info").output
            output shouldContain "Task '$GENERATE' is not up-to-date"
            output shouldNotContain "full rebuild"

            it read "$GENERATED/java/First.java" shouldContain "//int added = 1;"
            untouched.getLastModifiedTime() shouldBe marker
        }

        integrationTest("rerun after handler change") {
            runGradleIncremental(it, GENERATE)
            it read "$GENERATED/resources/data.cfg" shouldBe "#? if <2\n#value = 1\n"

            val controller = it.resolve("stonecutter.gradle.kts")
            controller.writeText(controller.readText().replace("line(\"#\")", "line(\"# \")"))
            runGradleIncremental(it, GENERATE)
            it read "$GENERATED/resources/data.cfg" shouldBe "#? if <2\n# value = 1\n"
        }

        integrationTest("use script lambda handler") {
            val classpath = "-PpluginClasspath=$PLUGIN_CLASSPATH"
            runGradle(it, GENERATE, classpath)
            it read "$GENERATED/resources/data.cfg" shouldBe "#\$ value\nVALUE = 2\n"

            val output = runGradle(it, GENERATE, classpath).output
            output shouldContain "Reusing configuration cache."
            it read "$GENERATED/resources/data.cfg" shouldBe "#\$ value\nVALUE = 2\n"
        }

        integrationTest("use interpreted lexer") {
            runGradle(it, GENERATE)
            it read "$GENERATED/resources/data.note" shouldBe "%? if <2\n%value = 1\n"
        }

        integrationTest("use reflected lexer") {
            runGradle(it, GENERATE)
            it read "$GENERATED/resources/data.note" shouldBe "#? if <2\n#value = 1\n"
        }
    }
}
