package v2.impl

import dev.kikugie.semver.data.Version
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.issue.BailException
import dev.kikugie.stitcher.issue.FullProblemFormatter
import dev.kikugie.stitcher.issue.ProblemReport
import dev.kikugie.stitcher.issue.ShortProblemFormatter
import dev.kikugie.stitcher.parser.ScannerAdapter
import dev.kikugie.stitcher.process
import dev.kikugie.stitcher.replacement.PerlReplacement
import dev.kikugie.stitcher.replacement.RegexReplacement
import dev.kikugie.stitcher.replacement.Replacement
import dev.kikugie.stitcher.replacement.StringReplacement
import dev.kikugie.stitcher.transformer.TransformParameters
import dev.kikugie.stitcher.transformer.strategy.CommentingStrategy
import dev.kikugie.stitcher.transformer.strategy.SwappingStrategy
import dev.kikugie.stitcher.transformer.strategy.UncommentingStrategy
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import org.antlr.v4.runtime.CharStreams
import org.intellij.lang.annotations.Language
import org.pcre4j.option.Pcre2CompileOption
import util.IMMEDIATE_THROW
import kotlin.io.path.Path

class ProcessTestSetup : TestSetup {
    val swaps: MutableMap<String, String> = mutableMapOf()
    val constants: MutableMap<String, Boolean> = mutableMapOf("true" to true, "false" to false)
    val dependencies: MutableMap<String, Version> = mutableMapOf()
    val replacements: MutableList<Replacement> = mutableListOf()

    var adapter: ScannerAdapter.Factory = Scanners.Java
    var commenter: CommentingStrategy = Commenters.JavaStar
    var uncommenter: UncommentingStrategy = Uncommenters.JavaLike
    var swapper: SwappingStrategy = Swappers.Standard
    private var onError: ((List<ProblemReport>) -> Unit)? = null

    lateinit var input: String
    lateinit var output: String

    override suspend fun TestScope.invoke() {
        val reporter = ProblemCollector()
        val result = try {
            val document = Document(Path("test@${testCase.name.name.replace(' ', '_')}"), CharStreams.fromString(input), IMMEDIATE_THROW)
            process(document, buildParameters())
        } catch (_: BailException) {
            reporter.complete(true, onError)
        }
        reporter.complete(false, onError)
        if (onError == null && ::output.isInitialized)
            result shouldBe output
    }

    operator fun MutableMap<String, Version>.set(key: String, value: String): Unit =
        set(key, Version.parse(value))

    fun string(from: String, to: String, id: String? = null, enable: Boolean = false): StringReplacement =
        StringReplacement(to, from, id, enable)

    fun regex(@Language("RegExp") from: String, to: String, vararg flags: RegexOption, id: String? = null, enable: Boolean = false): RegexReplacement {
        val flags = from.toRegex(flags.toSet()).toPattern().flags()
        return RegexReplacement(to, from, flags, id, enable)
    }

    fun perl(from: String, to: String, vararg flags: Pcre2CompileOption, id: String? = null, enable: Boolean = false): PerlReplacement {
        var mask = 0
        for (it in flags)
            mask = mask or it.value()
        return PerlReplacement(to, from, mask, id, enable)
    }

    fun errors(action: List<ProblemReport>.() -> Unit) {
        onError = action
    }

    private fun buildParameters() = TransformParameters(
        adapter, commenter, uncommenter, swapper,
        swaps, constants, dependencies, replacements
    )

    private class ProblemCollector : (ProblemReport) -> Unit {
        val problems: MutableList<ProblemReport> = mutableListOf()

        override fun invoke(report: ProblemReport) {
            System.err.println(report.accept(FullProblemFormatter))
            problems += report
        }

        fun complete(expected: Boolean, action: ((List<ProblemReport>) -> Unit)?): Any = when {
            action != null -> action(problems)
            problems.isNotEmpty() -> {
                val joined = problems.joinToString("\n") { "  " + it.accept(ShortProblemFormatter) }
                throw AssertionError("Processing failed:\n$joined")
            }
            expected -> throw AssertionError("Expected to encounter issues, but none were reported.")
            else -> {}
        }
    }
}