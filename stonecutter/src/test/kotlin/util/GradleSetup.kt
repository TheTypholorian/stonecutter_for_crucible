package util

import com.github.ajalt.mordant.rendering.TextColors
import io.kotest.core.descriptors.Descriptor
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestScope
import io.kotest.engine.spec.tempdir
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.*

private typealias GradleAction = (Array<out String>) -> BuildResult
class GradleRun(val dir: Path, build: BuildResult) : BuildResult by build

class GradleSetup(private val test: TestCase) : TestSetup {
    private class RunData(val args: Array<out String>, val run: (GradleRun) -> Unit, val fail: Boolean)

    private val runs: MutableList<RunData> = mutableListOf()
    var dir: String = buildString { test.descriptor.joinTo(this) }
        .substringAfter('.').replace(' ', '_').replace('-', '_')

    fun build(vararg args: String, action: GradleRun.() -> Unit = {}) {
        runs += RunData(args, action, false)
    }

    fun fail(vararg args: String, action: GradleRun.() -> Unit = {}) {
        runs += RunData(args, action, true)
    }

    override suspend fun TestScope.invoke() {
        val (dir, build) = prepare()
        for (it in runs)
            if (it.fail) it.run(GradleRun(dir, shouldFailBuild { build(it.args) }.buildResult))
            else it.run(GradleRun(dir, build(it.args)))
    }

    private fun prepare(): Pair<Path, GradleAction> {
        val source = Path("src/test/resources/projects/$dir")
        val temp = test.spec.tempdir(suffix = dir.replace('/', '-'), keepOnFailure = true).toPath()
        copyResources(source, temp)
        println(buildString {
            appendLine(TextColors.cyan("### Running test '$dir' ###"))
            appendLine(TextColors.cyan("- Source: file://${source.absolutePathString()}"))
            appendLine(TextColors.cyan("- Build: file://${temp.absolutePathString()}"))
        })

        val defaultArgs = listOf("--stacktrace", "--parallel", "--rerun-tasks", "--configuration-cache")
        val build = GradleRunner.create()
            .withProjectDir(temp.toFile())
            .withPluginClasspath()
            .forwardStdOutput(StyledPrintWriter(TextColors.white))
            .forwardStdError(StyledPrintWriter(TextColors.red))

        return temp to { build.withArguments(defaultArgs + it).build() }
    }

    private fun Descriptor.joinTo(builder: StringBuilder): Any = when (this) {
        is Descriptor.SpecDescriptor -> {
            builder.append(id.value)
        }

        is Descriptor.TestDescriptor -> {
            parent.joinTo(builder)
            builder.append('/')
            builder.append(id.value)
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun copyResources(source: Path, destination: Path) {
        if (source.notExists()) throw FileNotFoundException(source.absolutePathString())
        source.copyToRecursively(destination, followLinks = false, overwrite = true)
    }
}