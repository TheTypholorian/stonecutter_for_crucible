package dev.kikugie.stitcher.replacement

import dev.kikugie.stonecutter.model.process.RegexReplacement
import dev.kikugie.stonecutter.model.process.Replacement
import org.pcre4j.regex.appendMatch
import java.util.regex.MatchResult
import java.util.regex.Pattern

class RegexExecutor(replacements: List<Replacement>, override val identifiers: Set<String>) : ReplacementExecutor {
    private val replacements: List<Pair<Pattern, RegexReplacement>> = replacements.asSequence()
        .filterIsInstance<RegexReplacement>()
        .map { Pattern.compile(it.pattern, it.flags) to it }
        .toList()

    override fun collect(text: CharSequence, queue: EmitQueue) {
        for ((regex, repl) in replacements) if (repl.isActive)
            process(regex, repl.target, text, queue)
    }

    private fun process(regex: Pattern, target: String, text: CharSequence, queue: EmitQueue) {
        val matcher = regex.matcher(text)
        var index = 0
        while (index >= 0 && matcher.find(index)) {
            queue += RegexEmit(matcher.toMatchResult(), target)
            index = matcher.nextIndex(text)
        }
    }

    private class RegexEmit(val match: MatchResult, val value: String) : EmitQueue.Entry {
        override val start: Int get() = match.start()
        override val end: Int get() = match.end()

        override fun appendTo(builder: Appendable) {
            builder.appendMatch(match, value)
        }
    }
}
