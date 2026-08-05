import de.infix.testBalloon.framework.core.*
import de.infix.testBalloon.framework.core.TestFixture.Scope
import de.infix.testBalloon.framework.shared.AbstractTestElement
import de.infix.testBalloon.framework.shared.TestElementName
import de.infix.testBalloon.framework.shared.TestRegistering
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.paths.shouldBeAFile
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyToRecursively
import kotlin.io.path.deleteRecursively
import kotlin.io.path.readText

class ConcurrentTestSession : TestSession(
    defaultCompartment = { TestCompartment.Concurrent }
)

fun runGradle(dir: Path, vararg arguments: String): BuildResult = configureGradle {
    forwardOutput()

    withPluginClasspath()
    withProjectDir(dir.toFile())
    withArguments(*arguments, "--stacktrace", "--parallel", "--rerun-tasks", "--configuration-cache")
    testPlatform.environment("GRADLE_VERSION")?.let(::withGradleVersion)
}.build()

@OptIn(ExperimentalPathApi::class)
fun TestSuiteScope.tempDirFixture(prefix: String = "${testSuiteInScope.testElementPath}-"): TestFixture<Path> = testFixture {
    Files.createTempDirectory(Path("build/tmp"), prefix)
} closeWith {ok ->
    if (ok || testPlatform.environment("CI") != null) deleteRecursively()
    else println("Temporary directory: file://${absolutePathString()}")
}

@TestRegistering
@JvmName("integrationTestFixtureScoped")
@OptIn(ExperimentalPathApi::class)
fun Scope<suspend Test.ExecutionScope.(Path) -> Unit>.integrationTest(
    @TestElementName name: String,
    testConfig: TestConfig = TestConfig,
    fixtureScopedAction: suspend Test.ExecutionScope.(Path) -> Unit
): Unit = test(name, testConfig) {
    // Map test path to the resources' location
    Path("src/test/resources/projects", testElementPath.toPathString()).run {
        println("Source directory: file://${absolutePathString()}")
        copyToRecursively(it, followLinks = false, overwrite = true)
    }

    fixtureScopedAction(it)
}

infix fun Path.read(subpath: String): String =
    resolve(subpath).apply { shouldBeAFile() }.readText()

inline fun shouldFailBuild(block: () -> Unit): UnexpectedBuildFailure =
    shouldThrow<UnexpectedBuildFailure>(block)

private fun AbstractTestElement.Path.toPathString(): String = toString()
    .removeSurrounding("«", "»")
    .replace('\u00a0', '_')
    .replace('�', '_')
    .replace('⧸', '/')
    .replace('↘', '/')
    .replace('-', '_')
    .substringAfterLast('.')

private inline fun configureGradle(action: GradleRunner.() -> Unit): GradleRunner =
    GradleRunner.create().apply(action)
