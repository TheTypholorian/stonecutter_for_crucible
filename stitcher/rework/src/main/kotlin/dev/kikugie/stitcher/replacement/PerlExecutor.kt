package dev.kikugie.stitcher.replacement

import dev.kikugie.stonecutter.model.process.PerlReplacement
import dev.kikugie.stonecutter.model.process.Replacement
import org.pcre4j.Pcre2Code
import org.pcre4j.Pcre2MatchData
import org.pcre4j.option.Pcre2SubstituteOption
import org.pcre4j.option.Pcre2SubstituteOption.EXTENDED
import org.pcre4j.option.Pcre2SubstituteOption.REPLACEMENT_ONLY
import java.util.EnumSet

private val FLAGS: EnumSet<Pcre2SubstituteOption> = EnumSet.of(EXTENDED, REPLACEMENT_ONLY)

class PerlExecutor(replacements: List<Replacement>, override val identifiers: Set<String>) : ReplacementExecutor {
    private val replacements: List<Pair<Pcre2Code, PerlReplacement>> = replacements.asSequence()
        .filterIsInstance<PerlReplacement>()
        .map { Pcre2Code(it.pattern, it.flags.toEnumSet()) to it }
        .toList()

    override fun collect(text: CharSequence, queue: EmitQueue) {
        if (replacements.isEmpty()) return

        val text = text.toString()
        for ((pattern, repl) in replacements) if (repl.isActive)
            process(pattern, repl.target, text, queue)
    }

    private fun process(pattern: Pcre2Code, target: String, text: String, queue: EmitQueue) {
        val data = Pcre2MatchData(pattern)
        var start = 0
        while (start in text.indices) try {
            val value = pattern.substitute(text, start, FLAGS, data, null, target)
            val offsets = data.ovector()
            queue += PerlEmit(offsets[0].toInt(), offsets[1].toInt(), value)
            start = offsets[1].toInt()
        } catch (e: RuntimeException) {
            // TODO: Report this to the document
            break
        }
    }

    private class PerlEmit(override val start: Int, override val end: Int, val value: String) : EmitQueue.Entry {
        override fun appendTo(builder: Appendable) {
            builder.append(value)
        }
    }
}