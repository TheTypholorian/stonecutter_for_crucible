import dev.kikugie.stitcher.issue.ProblemReporterImpl
import dev.kikugie.stitcher.replacement.PerlReplacement
import dev.kikugie.stitcher.replacement.RegexReplacement
import dev.kikugie.stitcher.replacement.Replacement
import dev.kikugie.stitcher.replacement.ReplacementExecutor
import dev.kikugie.stitcher.replacement.StringReplacement
import dev.kikugie.stitcher.util.toLong
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.pcre4j.option.Pcre2CompileOption
import util.IMMEDIATE_THROW
import java.util.EnumSet
import kotlin.io.path.Path

class ReplacementTest : FunSpec({
    context("string replacements") {
        test("simple string") {
            buildList {
                string("Hornet", "Lace")
            } apply "Hello Hornet!" shouldBe "Hello Lace!"
        }

        test("contained string") {
            buildList {
                string("234", "12345")
            } apply "0123456" shouldBe "0123456"
        }

        test("overlap first") {
            buildList {
                string("ABC", "123")
                string("BCD", "345")
            } apply "ABCD" shouldBe "123D"
        }

        test("overlap longest") {
            buildList {
                string("ABC", "123")
                string("ABCD", "345")
            } apply "ABCDE" shouldBe "345E"
        }
    }

    context("regex replacements") {
        test("simple pattern with groups") {
            buildList {
                regex("%(\\d+)%", "§$1§")
            } apply "%123% %abc% %1%" shouldBe "§123§ %abc% §1§"
        }
    }

    context("perl replacements") {
        test("extended patterns") {
            buildList {
                perl("Blocks\\.DYED_TERRACOTTA\\.(\\w+)\\(\\)", "Blocks.\\U$1_TERRACOTTA")
            } apply """
                Blocks.DYED_TERRACOTTA.red()
                Blocks.DYED_TERRACOTTA.blue()
                Blocks.DYED_TERRACOTTA
            """.trimIndent() shouldBe """
                Blocks.RED_TERRACOTTA
                Blocks.BLUE_TERRACOTTA
                Blocks.DYED_TERRACOTTA
            """.trimIndent()
        }

        test("testing") {
            val colorsLower = "black|blue|brown|cyan|gray|green|lime|magenta|orange|pink|purple|red|white|yellow"
            val colorsUpper = "BLACK|BLUE|BROWN|CYAN|GRAY|GREEN|LIME|MAGENTA|ORANGE|PINK|PURPLE|RED|WHITE|YELLOW"
            val withFunc = """
                private fun SafeItemStack.isPaused(): Boolean = this.`is`(Items.DYED_TERRACOTTA.red())
                private fun SafeItemStack.isPaused(): Boolean = this.`is`(Items.DYED_TERRACOTTA.red())
            """.trimIndent()
            val withField =  """
                private fun SafeItemStack.isPaused(): Boolean = this.`is`(Items.RED_TERRACOTTA)
                private fun SafeItemStack.isPaused(): Boolean = this.`is`(Items.RED_TERRACOTTA)
            """.trimIndent()
            buildList {
                perl("DYED_TERRACOTTA\\.(?<color>$colorsLower)\\(\\)", $$"\\U${color}_TERRACOTTA")
            }.let {
                it apply withFunc shouldBe withField
                it apply withField shouldBe withField
            }
            buildList {
                perl( "(?<color>$colorsUpper)_TERRACOTTA", $$"DYED_TERRACOTTA.\\L${color}()")
            }.let {
                it apply withField shouldBe withFunc
                it apply withFunc shouldBe withFunc
            }
        }
    }
})

private infix fun List<Replacement>.apply(text: String): String {
    val reporter = ProblemReporterImpl(Path(""), text, IMMEDIATE_THROW)
    val executor = ReplacementExecutor(this, reporter)
    val builder = StringBuilder(text)
    executor.replace(0, builder)
    return builder.toString()
}

private fun MutableList<Replacement>.string(from: String, to: String, id: String? = null) {
    val (id, enabled) = id.unwrap()
    this += StringReplacement(to, from, id, enabled)
}

private fun MutableList<Replacement>.regex(@Language("RegExp") from: String, to: String, id: String? = null, vararg options: RegexOption) {
    val (id, enabled) = id.unwrap()
    val regex = Regex(from, options.toSet()).toPattern()
    this += RegexReplacement(to, regex.pattern(), regex.flags(), id, enabled)
}

private fun MutableList<Replacement>.perl(@Language("RegExp") from: String, to: String, id: String? = null, vararg options: Pcre2CompileOption) {
    val (id, enabled) = id.unwrap()
    val set = EnumSet.noneOf(Pcre2CompileOption::class.java)
    set.addAll(options.toList())
    this += PerlReplacement(to, from, set.toLong().toInt(), id, enabled)
}

private fun String?.unwrap(): Pair<String?, Boolean> {
    val id = this ?: return null to false
    return id.trimStart('!') to id.startsWith('!')
}
