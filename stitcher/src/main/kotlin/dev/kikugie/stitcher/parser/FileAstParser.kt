@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.parser

import dev.kikugie.stitcher.document.DocumentReference
import dev.kikugie.stitcher.document.issue.LAYOUT_GROUP
import dev.kikugie.stitcher.document.issue.ProblemReport.Severity
import dev.kikugie.stitcher.document.issue.report
import dev.kikugie.stitcher.token.*
import dev.kikugie.stitcher.token.DefinitionToken.*
import dev.kikugie.stitcher.token.type.RootScopeState
import dev.kikugie.stitcher.util.*

object FileAstParser {
    fun parse(stream: FileTokenStream): BlockToken.Root {
        val builder = RootScopeBuilder().apply { accept(stream) }
        return BlockToken.Root(stream.document, RootScopeState.ANY, ArrayList<BlockToken>().apply {
            builder.collect(this)
        })
    }
}

private interface FileAstBuilder {
    fun accept(stream: FileTokenStream)
    fun collect(list: MutableList<BlockToken>)
}

private class ContentBuilder(val token: BlockToken.Content) : FileAstBuilder {
    val text: String inline get() = token.ref.text

    override fun accept(stream: FileTokenStream): Unit = Unit
    override fun collect(list: MutableList<BlockToken>): Unit = list.plusAssign(token)
}

private class CommentBuilder(val token: BlockToken.Comment) : FileAstBuilder {
    val text: String inline get() = token.body.ref.text

    override fun accept(stream: FileTokenStream): Unit = Unit
    override fun collect(list: MutableList<BlockToken>): Unit = list.plusAssign(token)
}

private class InertCodeBuilder(val token: BlockToken.Code) : FileAstBuilder {
    override fun accept(stream: FileTokenStream): Unit = Unit
    override fun collect(list: MutableList<BlockToken>): Unit = list.plusAssign(token)
}

private open class ScopeBuilder : FileAstBuilder {
    val entries: MutableList<FileAstBuilder> = ArrayList()
    var scopeIsNotBlank: Boolean = false

    override fun accept(stream: FileTokenStream) {
        while (true) this += when (val next: BlockToken = stream.peek(1) ?: break) {
            is BlockToken.Content -> processContent(next, stream)
            is BlockToken.Comment -> processComment(next, stream)
            is BlockToken.Code -> processCode(next, stream)
            is BlockToken.Root -> continue
        } ?: break
    }

    override fun collect(list: MutableList<BlockToken>) {
        for (it in entries) it.collect(list)
    }

    protected operator fun plusAssign(builder: FileAstBuilder) {
        entries += builder
        scopeIsNotBlank = scopeIsNotBlank || builder.isNotBlank()
    }

    protected fun buildNested(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder =
        newCodeScopeBuilder(token).apply { accept(stream.advance()) }

    // Create and process a new [FileAstBuilder] from the [token], or null if this scope can't accept any more.
    // It's the responsibility of the implementation to advance the [stream] if the result is non-null.
    protected open fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? =
        buildNested(token, stream)

    protected open fun processContent(token: BlockToken.Content, stream: FileTokenStream): FileAstBuilder? =
        ContentBuilder(token).also { stream.consume() }

    protected open fun processComment(token: BlockToken.Comment, stream: FileTokenStream): FileAstBuilder? =
        CommentBuilder(token).also { stream.consume() }
}

// The file's top-level scope. A closer reaching here terminates nothing, so it's reported as unmatched.
private class RootScopeBuilder : ScopeBuilder() {
    override fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? {
        if (token.definition.role.isTerminator) reportRootCloser(token)
        return super.processCode(token, stream)
    }
}

private abstract class CodeScopeBuilder(val token: BlockToken.Code, val parent: ScopeBuilder) : ScopeBuilder() {
    var child: FileAstBuilder? = null

    protected var foundSplitMatch: Boolean = false

    final override fun accept(stream: FileTokenStream) {
        super.accept(stream)
        // A terminator (own closer, branch, or a claimed foreign closer) becomes the [child]; without one the
        // scope ran to the stream's end. Handled here as it needs the tokens that follow.
        if (child == null) onUnterminated(stream)
    }

    override fun collect(list: MutableList<BlockToken>) {
        list += token.copy(scope = ArrayList<BlockToken>().apply {
            for (it in entries) it.collect(this)
        })
        child?.collect(list)
    }

    override fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? = when (def.determineAction(token.definition)) {
        MatchAction.CLAIM -> claimCodeBuilder(newCodeScopeBuilder(token), stream)
        MatchAction.YIELD -> null
        MatchAction.NEST -> nestCode(token, stream)
    }

    protected abstract fun nestCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder?

    protected open fun onUnterminated(stream: FileTokenStream): Unit = Unit

    protected fun claimCodeBuilder(builder: FileAstBuilder, stream: FileTokenStream): Nothing? {
        child = builder.apply { accept(stream.advance()) }
        return null
    }

    // Line and lookup branches finish before their extension, which sits past a blank gap. This absorbs that
    // gap and claims the extension as the [child], continuing the condition chain. The gap may hold any mix
    // of blank content and blank comment blocks (their openers/closers aside).
    protected fun claimTrailingExtension(stream: FileTokenStream) {
        val distance: Int = findTrailingExtension(stream) ?: return
        repeat(distance - 1) { this += blankGapBuilder(stream.peek(1)!!).also { stream.consume() } }
        claimCodeBuilder(newCodeScopeBuilder(stream.peek(1) as BlockToken.Code), stream)
    }

    protected fun processSplitContent(token: BlockToken.Content, stream: FileTokenStream, split: Int): FileAstBuilder? = when (split) {
        // Match found exactly at the start of the block, so it's excluded
        0 -> null

        // Match not foun, so try searching next
        -1 -> super.processContent(token, stream)

        // Match found, capturing the entire token
        token.leaf.length -> {
            foundSplitMatch = true
            super.processContent(token, stream)
        }

        // Match found in the middle, so return the consumed part and insert the remainder
        else -> with(token) {
            foundSplitMatch = true
            val first = BlockToken.Content(leaf.withRef { copy(end = start + split) })
            val second = BlockToken.Content(leaf.withRef { copy(start = start + split) })
            super.processContent(first, stream).also { stream.insert(second) }
        }
    }

    protected fun processScopedComment(token: BlockToken.Comment, stream: FileTokenStream, split: Int): FileAstBuilder? = when (split) {
        0 -> null
        -1 -> super.processComment(token, stream)
        else -> {
            foundSplitMatch = true
            super.processComment(token, stream)
        }
    }
}

private abstract class ScopedScopeBuilder(token: BlockToken.Code, parent: ScopeBuilder) : CodeScopeBuilder(token, parent) {
    override fun nestCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? =
        buildNested(token, stream).also {
            // A closer of another construct can't terminate this scope, so it's flagged rather than silently nested.
            if (token.definition.role.isTerminator) reportInvalidCloserType(this.token, token)
        }

    override fun onUnterminated(stream: FileTokenStream): Unit = reportMissingClosingBracket(token)
}

private abstract class LineScopeBuilder(token: BlockToken.Code, parent: ScopeBuilder) : CodeScopeBuilder(token, parent) {
    protected var midLineContinuation: Boolean = false

    override fun processContent(token: BlockToken.Content, stream: FileTokenStream): FileAstBuilder? {
        val freshLine: Boolean = !midLineContinuation
        midLineContinuation = false
        // Real content on a fresh line after the extended body ends the branch - it's no longer governed by
        // the condition. This also bounds a commented body (whose line breaks live in comment closers, not
        // in any content the split inspects) at the first genuine code line, e.g. a closing `}`. Content
        // continuing the same line past an inline nested block is still captured, split at the line break.
        return if (freshLine && scopeIsNotBlank && token.leaf.text.isNotBlank()) null
        else processSplitContent(token, stream, token.findLineSplit(scopeIsNotBlank))
    }

    override fun processComment(token: BlockToken.Comment, stream: FileTokenStream): FileAstBuilder? {
        midLineContinuation = false
        return processScopedComment(token, stream, token.findLineSplit(scopeIsNotBlank))
    }

    // A definition following real content on the line starts a separate construct and is left for the
    // enclosing scope (it becomes a sibling, not a nested child). On a still-blank line it nests and owns
    // the rest of the line, as before.
    override fun nestCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? =
        if (scopeIsNotBlank) null else buildNested(token, stream)
}

private abstract class LookupScopeBuilder(token: BlockToken.Code, parent: ScopeBuilder) : CodeScopeBuilder(token, parent) {
    protected val lookup: String?
    protected val capturing: Boolean

    init {
        val opener = token.definition.opener as? ScopeToken.Lookup
        lookup = opener?.lookup?.unquote()
        capturing = opener?.plus != null
    }

    // Once the match is found the scope is complete; further tokens belong to the enclosing scope. Without
    // this the remainder past the match (or a later token that happens to match again) would be re-captured.
    override fun processContent(token: BlockToken.Content, stream: FileTokenStream): FileAstBuilder? =
        if (foundSplitMatch) null else processSplitContent(token, stream, token.findLookupSplit(lookup, capturing))

    override fun processComment(token: BlockToken.Comment, stream: FileTokenStream): FileAstBuilder? =
        if (foundSplitMatch) null else processScopedComment(token, stream, token.findLookupSplit(lookup, capturing))

    override fun nestCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? =
        if (scopeIsNotBlank) null else {
            // The search shouldn't continue past the nested scope.
            foundSplitMatch = true
            buildNested(token, stream)
        }
}

private class ScopedConditionBuilder(token: BlockToken.Code, parent: ScopeBuilder) : ScopedScopeBuilder(token, parent)

private class LineConditionBuilder(token: BlockToken.Code, parent: ScopeBuilder) : LineScopeBuilder(token, parent) {
    override fun onUnterminated(stream: FileTokenStream): Unit = claimTrailingExtension(stream)
}

private class LookupConditionBuilder(token: BlockToken.Code, parent: ScopeBuilder) : LookupScopeBuilder(token, parent) {
    override fun onUnterminated(stream: FileTokenStream): Unit = claimTrailingExtension(stream)
}

private class ScopedReplacementBuilder(token: BlockToken.Code, parent: ScopeBuilder) : ScopedScopeBuilder(token, parent)

private class LineReplacementBuilder(token: BlockToken.Code, parent: ScopeBuilder) : LineScopeBuilder(token, parent)

private class LookupReplacementBuilder(token: BlockToken.Code, parent: ScopeBuilder) : LookupScopeBuilder(token, parent)

private class ScopedSwapBuilder(token: BlockToken.Code, parent: ScopeBuilder) : ScopedScopeBuilder(token, parent) {
    override fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? = when (def.determineAction(token.definition)) {
        MatchAction.CLAIM -> claimCodeBuilder(newCodeScopeBuilder(token), stream)
        else -> ignoreInSwap(token, stream)
    }
}

private class LineSwapBuilder(token: BlockToken.Code, parent: ScopeBuilder) : LineScopeBuilder(token, parent) {
    override fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? {
        if (anyAncestor { it.def.isTerminatedBy(token.definition) }) return null

        // Handles the cases where a code block is found inside the line scope:
        // - If it spans multiple lines, it is included in the scope entirely, and the search ends.
        //   This is the case because leaving partial code comments could cause problems.
        // - If it's a fully inline block comment (its closer keeps the line open), the search proceeds to
        //   account for the rest of the line.
        // - A line comment closer ends the line, so what follows is a fresh line and bounds the scope.
        if (token.host.findLineSplit(immediate = scopeIsNotBlank) !in -1..0) foundSplitMatch = true
        else if ('\n' !in token.host.closer?.text.orEmpty()) midLineContinuation = true

        return ignoreInSwap(token, stream)
    }
}

private class LookupSwapBuilder(token: BlockToken.Code, parent: ScopeBuilder) : LookupScopeBuilder(token, parent) {
    private var hasSearchedForMatch: Boolean = false

    override fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? = null

    override fun processContent(token: BlockToken.Content, stream: FileTokenStream): FileAstBuilder? {
        tryFindMatch(stream)
        return if (matchesAtScopeStart(token)) markEmptyMatch(token, stream)
        else super.processContent(token, stream)
    }

    override fun processComment(token: BlockToken.Comment, stream: FileTokenStream): FileAstBuilder? {
        tryFindMatch(stream)
        return if (matchesAtScopeStart(token)) markEmptyMatch(token, stream)
        else super.processComment(token, stream)
    }

    private fun tryFindMatch(stream: FileTokenStream) {
        if (hasSearchedForMatch) return

        hasSearchedForMatch = true
        if (!stream.hasLookupMatch(lookup, capturing)) foundSplitMatch = true
    }

    private fun matchesAtScopeStart(token: BlockToken): Boolean = !foundSplitMatch
        && entries.isEmpty()
        && token.findLookupSplit(lookup, capturing) == 0

    private fun markEmptyMatch(token: BlockToken, stream: FileTokenStream): FileAstBuilder {
        foundSplitMatch = true
        stream.consume()
        stream.insert(token)
        return ContentBuilder(BlockToken.Content(token.ref.copy(end = token.ref.start)))
    }
}

private enum class MatchAction {
    CLAIM, YIELD, NEST,
}

private fun DefinitionToken.determineAction(incoming: DefinitionToken): MatchAction = when {
    // Statements never terminate each other, so a foreign marker can only be nested. This compares [kind],
    // not [type]: an opener and the closer that ends it are the same construct but different token types.
    incoming.kind != kind -> MatchAction.NEST

    // A `}`-bearing marker - a closer or a `} else` branch alike - terminates the nearest enclosing *scoped*
    // block. A line or lookup scope has no `{` for it to match, so the scope ends here and the marker is left
    // to the block that does, however little of the line the scope has covered so far.
    incoming.closer != null && body != Body.SCOPED -> MatchAction.YIELD

    // A branch continues this scope's chain, and a closer finishes it. Both belong to the scope they terminate.
    incoming.role == Role.EXTENSION -> MatchAction.CLAIM
    incoming.role == Role.CLOSER && body == Body.SCOPED -> MatchAction.CLAIM

    else -> MatchAction.NEST
}

private fun DefinitionToken.isTerminatedBy(incoming: DefinitionToken): Boolean = when {
    kind != incoming.kind -> false
    body == Body.SCOPED -> incoming.role.isTerminator
    body == Body.LINE || body == Body.LOOKUP -> incoming.role == Role.EXTENSION
    else -> false
}

context(parent: ScopeBuilder)
private fun newCodeScopeBuilder(token: BlockToken.Code): FileAstBuilder = when (token.definition.body) {
    Body.NONE -> InertCodeBuilder(token)

    Body.SCOPED -> when (token.definition.kind) {
        Kind.CONDITION -> ScopedConditionBuilder(token, parent)
        Kind.REPLACEMENT -> ScopedReplacementBuilder(token, parent)
        Kind.SWAP -> ScopedSwapBuilder(token, parent)
    }

    Body.LINE -> when (token.definition.kind) {
        Kind.CONDITION -> LineConditionBuilder(token, parent)
        Kind.REPLACEMENT -> LineReplacementBuilder(token, parent)
        Kind.SWAP -> LineSwapBuilder(token, parent)
    }

    Body.LOOKUP -> when (token.definition.kind) {
        Kind.CONDITION -> LookupConditionBuilder(token, parent)
        Kind.REPLACEMENT -> LookupReplacementBuilder(token, parent)
        Kind.SWAP -> LookupSwapBuilder(token, parent)
    }
}

private fun CodeScopeBuilder.findTrailingExtension(stream: FileTokenStream): Int? {
    var offset = 1
    while (true) when (val next: BlockToken = stream.peek(offset) ?: return null) {
        // Only a branch this scope may own ends the search successfully; a `}`-bearing one belongs to an
        // enclosing block and leaves the chain unextended.
        is BlockToken.Code -> return offset.takeIf { def.determineAction(next.definition) == MatchAction.CLAIM }
        is BlockToken.Content if next.leaf.text.isBlank() -> offset++
        is BlockToken.Comment if next.body.text.isBlank() -> offset++
        else -> return null
    }
}

private fun blankGapBuilder(block: BlockToken): FileAstBuilder = when (block) {
    is BlockToken.Content -> ContentBuilder(block)
    is BlockToken.Comment -> CommentBuilder(block)
    else -> error("Unexpected token in trailing extension gap: $block")
}

context(scope: CodeScopeBuilder)
private fun ignoreInSwap(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder {
    stream.consume()
    reportSwapNesting(token)
    return InertCodeBuilder(token)
}

private inline fun FileTokenStream.advance(): FileTokenStream =
    apply(FileTokenStream::consume)

context(parent: CodeScopeBuilder)
private fun reportSwapNesting(block: BlockToken.Code): Unit = block.ref.report(LAYOUT_GROUP + "definition-in-swap-scope") {
    severity = Severity.WARN
    label = "Ignored ${block.name()}"
    info = "${block.title()}s and other statements are not processed inside swap scopes."
    hint = "Swap scope is defined at this location:\n${parent.ref.toUriString()}"
}

context(parent: RootScopeBuilder)
private fun reportRootCloser(closer: BlockToken.Code): Unit = closer.ref.report(LAYOUT_GROUP + "unmatched-root-closer") {
    label = "Unmatched ${closer.name()} closer"
    info = "${closer.title()} must have a corresponding `${closer.marker.text} ... {` comment."

    val candidate: BlockToken.Code? = parent.findScopeCandidate(closer)
    if (candidate != null) hint = "${candidate.title()} at this location may need to end with `{`:\n${candidate.ref.toUriString()}"
}

// Searches the tree built so far for an open scope that was likely meant to be closed - i.e. one written
// without a `{`. The parsed-so-far entries are complete, so they're collected and searched as plain tokens.
private fun RootScopeBuilder.findScopeCandidate(closer: BlockToken.Code): BlockToken.Code? =
    ArrayList<BlockToken>().apply { collect(this) }.findScopeCandidate(closer)

private fun List<BlockToken>.findScopeCandidate(closer: BlockToken.Code): BlockToken.Code? {
    for (token in asReversed()) {
        if (token !is BlockToken.Code) continue
        if (token === closer || token.marker neqtype closer.marker) continue
        token.scope.findScopeCandidate(closer)?.let { return it }
        if (token.definition.body == Body.LINE) return token
    }
    return null
}

private fun reportMissingClosingBracket(code: BlockToken.Code): Unit = code.ref.report(LAYOUT_GROUP + "missing-scope-closer") {
    label = "Missing scope closer"
    info = "${code.title()} doesn't have a matching `${code.marker.text}}` closer."
    hint = "Add a `${code.marker.text}}` comment to close the scope opened here."
}

private fun reportInvalidCloserType(opener: BlockToken.Code, closer: BlockToken.Code): Unit =
    closer.ref.report(LAYOUT_GROUP + "mismatched-closer-type") {
        severity = Severity.WARN
        label = "Mismatched closer type"
        info = "${opener.title()} can't be closed with a ${closer.name()} marker."
        hint = buildString {
            appendLine("${opener.title()} at this location needs to be closed with `${opener.marker.text}}`:")
            append(opener.ref.toUriString())
        }
    }

private val CodeScopeBuilder.def: DefinitionToken
    inline get() = token.definition

private val CodeScopeBuilder.ref: DocumentReference
    inline get() = token.ref

private val Role.isTerminator: Boolean
    get() = this == Role.CLOSER || this == Role.EXTENSION

private fun BlockToken.title(): String =
    name().titlecase()

private fun BlockToken.name(): String = when (this) {
    is BlockToken.Content -> "text"
    is BlockToken.Comment -> "comment"
    is BlockToken.Root -> "root"
    is BlockToken.Code -> when (definition) {
        is Swap -> "swap"
        is Condition -> "condition"
        is Replacement -> "replacement"
    }
}

private fun FileAstBuilder.isNotBlank(): Boolean = when (this) {
    is ContentBuilder -> text.isNotBlank()
    is CommentBuilder -> text.isNotBlank()
    else -> true
}

private inline fun CodeScopeBuilder.anyAncestor(action: (CodeScopeBuilder) -> Boolean): Boolean {
    var next: ScopeBuilder = parent
    while (next is CodeScopeBuilder) {
        if (action(next)) return true
        next = next.parent
    }
    return false
}

private fun BlockToken.Content.findLineSplit(immediate: Boolean): Int =
    leaf.text.findLineSplit(immediate)

private fun BlockToken.Comment.findLineSplit(immediate: Boolean): Int {
    val split = body.text.findLineSplit(immediate)
    return if (split >= 0 || body.text.isBlank() || '\n' !in opener?.text.orEmpty()) split else body.text.length
}

private fun FileTokenStream.hasLookupMatch(match: String?, capturing: Boolean): Boolean {
    var offset = 1
    while (true) when (val next: BlockToken = peek(offset++) ?: return false) {
        is BlockToken.Content, is BlockToken.Comment -> if (next.findLookupSplit(match, capturing) != -1) return true
        else -> return false
    }
}

private fun BlockToken.findLookupSplit(match: String?, capturing: Boolean): Int = when (this) {
    is BlockToken.Content -> findLookupSplit(match, capturing)
    is BlockToken.Comment -> findLookupSplit(match, capturing)
    else -> -1
}

private fun BlockToken.Content.findLookupSplit(match: String?, capturing: Boolean): Int = when (match) {
    null -> leaf.text.findWordSplit()
    else -> leaf.text.findLookupSplit(match, capturing)
}

private fun BlockToken.Comment.findLookupSplit(match: String?, capturing: Boolean): Int = when (match) {
    null -> body.text.findWordSplit()
    else -> body.text.findLookupSplit(match, capturing)
}
