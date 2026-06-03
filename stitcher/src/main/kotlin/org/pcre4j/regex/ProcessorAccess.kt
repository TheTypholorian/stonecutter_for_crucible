package org.pcre4j.regex

import java.util.regex.MatchResult

internal fun Appendable.appendMatch(match: MatchResult, value: String, index: Map<String, Int> = match.namedGroups()): Unit =
    MatcherReplacementProcessor.appendReplacement(this, value, match, index)
