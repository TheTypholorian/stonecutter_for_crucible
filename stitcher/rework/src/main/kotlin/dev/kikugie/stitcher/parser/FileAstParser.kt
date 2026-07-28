@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.parser

import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.StitcherParser.Tokens
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.DocumentReference
import dev.kikugie.stitcher.document.issue.LAYOUT_GROUP
import dev.kikugie.stitcher.document.issue.ProblemReport.Severity
import dev.kikugie.stitcher.document.issue.report
import dev.kikugie.stitcher.token.*
import dev.kikugie.stitcher.token.DefinitionToken.Kind
import dev.kikugie.stitcher.util.*

object FileAstParser {
    fun parse(stream: FileTokenStream): BlockToken.Root =
        RootBuilder(stream.document).apply { accept(stream) }.build()
}

private sealed class BlockTokenBuilder {
    abstract val stub: BlockToken
    abstract val parent: BlockTokenBuilder

    open fun accept(stream: FileTokenStream) {}
    open fun build(): BlockToken = stub

    override fun toString(): String = "${this::class.simpleName}($stub)"

    companion object : BlockToken.Visitor<BlockTokenBuilder, BlockTokenBuilder> {
        context(parent: BlockTokenBuilder)
        inline operator fun invoke(token: BlockToken): BlockTokenBuilder =
            token.accept(this, parent)

        override fun visitContent(content: BlockToken.Content, ctx: BlockTokenBuilder): BlockTokenBuilder =
            ContentBuilder(content, ctx)

        override fun visitComment(comment: BlockToken.Comment, ctx: BlockTokenBuilder): BlockTokenBuilder =
            CommentBuilder(comment, ctx)

        override fun visitRoot(root: BlockToken.Root, ctx: BlockTokenBuilder): BlockTokenBuilder =
            throw UnsupportedOperationException()

        override fun visitCode(code: BlockToken.Code, ctx: BlockTokenBuilder): BlockTokenBuilder = when (code.marker.typeValue) {
            Tokens.SWAP_MARK -> SwapBuilder(code, ctx)
            Tokens.COND_MARK -> ConditionBuilder(code, ctx)
            Tokens.REPL_MARK -> ReplacementBuilder(code, ctx)
            else -> throw IllegalArgumentException("Invalid definition type ${code.marker.type}")
        }
    }
}

private class ContentBuilder(override val stub: BlockToken.Content, override val parent: BlockTokenBuilder) : BlockTokenBuilder() {
    val text: String inline get() = stub.leaf.text
}

private class CommentBuilder(override val stub: BlockToken.Comment, override val parent: BlockTokenBuilder) : BlockTokenBuilder() {
    val text: String inline get() = stub.body.text
}

private sealed class ScopeBuilder : BlockTokenBuilder() {
    val entries: MutableList<BlockTokenBuilder> = ArrayList()

    // [Content] and [Comment] tokens are blank if the body is blank - [Code] is not
    protected var scopeIsNotBlank: Boolean = false

    // Adds this token to [entries], parses its children and returns the created builder
    protected fun acceptToken(token: BlockToken, stream: FileTokenStream): BlockTokenBuilder = with(BlockTokenBuilder(token)) {
        entries += this
        scopeIsNotBlank = scopeIsNotBlank || isNotBlank()

        stream.consume()
        acceptChild(this, stream)
        this
    }

    // Parses [builder]'s scope, and if the next token is its closer, appends it to [entries]
    protected open fun acceptChild(builder: BlockTokenBuilder, stream: FileTokenStream) {
        builder.accept(stream)

        val code = builder as? CodeBuilder ?: return
        val next = stream.peek(1) as? BlockToken.Code ?: return
        if (code.terminatedBy(next)) acceptToken(next, stream)
    }
}

private class RootBuilder(document: Document) : ScopeBuilder() {
    override val stub: BlockToken.Root = BlockToken.Root(document, emptyList())
    override val parent: BlockTokenBuilder get() = this

    override fun accept(stream: FileTokenStream) {
        while (true) {
            // A closer reaching the top-level loop terminates nothing - matched closers are consumed by [acceptChild]
            val builder = acceptToken(stream.peek(1) ?: break, stream)
            if (builder is CodeBuilder && builder.kind.isCloser) reportRootCloser(builder)
        }
    }

    override fun build(): BlockToken.Root =
        stub.copy(scope = entries.map(BlockTokenBuilder::build))
}

private sealed class CodeBuilder(override val stub: BlockToken.Code, override val parent: BlockTokenBuilder) : ScopeBuilder() {
    val marker: LeafToken inline get() = stub.marker
    val definition: DefinitionToken inline get() = stub.definition
    val kind: Kind inline get() = definition.kind

    protected var satisfiedLookup: Boolean = false

    override fun build(): BlockToken =
        stub.copy(scope = entries.map(BlockTokenBuilder::build))

    override fun accept(stream: FileTokenStream): Unit = when (kind) {
        Kind.SCOPED_OPENER, Kind.SCOPED_EXTENSION -> acceptScoped(stream)
        Kind.LINE_OPENER, Kind.LINE_EXTENSION -> acceptLine(stream).also { acceptChainGap(stream) }
        Kind.LOOKUP_OPENER, Kind.LOOKUP_EXTENSION -> acceptLookup(stream).also { acceptChainGap(stream) }
        Kind.CLOSER, Kind.INDEPENDENT -> {}
    }

    protected fun acceptScoped(stream: FileTokenStream) {
        while (true) when (val next: BlockToken = stream.peek(1) ?: return reportMissingClosingBracket(this)) {
            is BlockToken.Code if terminatedBy(next) -> break
            is BlockToken.Code if isMismatchedCloser(next) -> {
                reportInvalidCloserType(acceptToken(next, stream) as CodeBuilder)
                break
            }

            else -> acceptToken(next, stream)
        }
    }

    // A closer of a different marker ends a typed scope with an error; swaps instead absorb it as ignored nesting.
    protected open fun isMismatchedCloser(next: BlockToken.Code): Boolean =
        next.definition.kind.isCloser

    protected fun acceptLine(stream: FileTokenStream) {
        while (true) when (val next: BlockToken = stream.peek(1) ?: break) {
            is BlockToken.Content -> if (acceptSplit(next, stream, next.findLineSplit(scopeIsNotBlank))) break
            is BlockToken.Comment -> if (acceptSplit(next, stream, next.findLineSplit(scopeIsNotBlank))) break
            is BlockToken.Code if terminatedBy(next) -> break
            // A definition opening the line nests and owns the rest of it; one after content ends the scope before it.
            is BlockToken.Code -> { if (!scopeIsNotBlank) acceptToken(next, stream); break }
            is BlockToken.Root -> break
        }
    }

    protected fun acceptLookup(stream: FileTokenStream) {
        // The definition that ended the search early - a nested one may hide the match, a closer cuts it short.
        var interrupter: BlockToken.Code? = null
        while (true) when (val next: BlockToken = stream.peek(1) ?: break) {
            is BlockToken.Content -> if (acceptSplit(next, stream, next.findLookupSplit())) { satisfiedLookup = true; break }
            is BlockToken.Comment -> if (acceptSplit(next, stream, next.findLookupSplit())) { satisfiedLookup = true; break }
            is BlockToken.Code if terminatedBy(next) -> break
            // A closer belongs to an enclosing scope and stops the search without being consumed.
            is BlockToken.Code if next.definition.kind.isCloser -> { interrupter = next; break }
            // A lookup keeps searching for its match across nested definitions.
            is BlockToken.Code -> { interrupter = interrupter ?: next; acceptToken(next, stream) }
            is BlockToken.Root -> break
        }

        // Word lookups (no literal) always match the next word, so only a specific string can go unfound.
        val opener = definition.opener
        if (!satisfiedLookup && opener is ScopeToken.Lookup && opener.lookup != null)
            reportLookupStringNotFound(this, opener, interrupter)
    }

    private fun acceptChainGap(stream: FileTokenStream) {
        val distance = findChainExtension(stream) ?: return
        repeat(distance - 1) { acceptToken(stream.peek(1)!!, stream) }
    }

    private fun findChainExtension(stream: FileTokenStream): Int? {
        var offset = 1
        while (true) when (val next: BlockToken = stream.peek(offset) ?: return null) {
            is BlockToken.Code -> return if (terminatedBy(next)) offset else null
            is BlockToken.Content if next.leaf.text.isBlank() -> offset++
            else -> return null
        }
    }

    private fun acceptSplit(token: BlockToken.Content, stream: FileTokenStream, split: Int): Boolean = when (split) {
        0 -> Unit
        AntlrToken.EOF -> acceptToken(token, stream)
        token.leaf.length -> acceptToken(token, stream)
        else -> with(token) {
            val prev = leaf.withRef { copy(end = start + split) }
            acceptToken(copy(leaf = prev), stream)

            val next = leaf.withRef { copy(start = start + split) }
            stream.insert(copy(leaf = next))
        }
    }.let {
        split >= 0
    }

    private fun acceptSplit(token: BlockToken.Comment, stream: FileTokenStream, split: Int): Boolean = when (split) {
        0 -> Unit
        AntlrToken.EOF -> acceptToken(token, stream)
        token.body.length -> acceptToken(token, stream)
        else -> with(token) {
            val prev = body.withRef { copy(end = start + split) }
            acceptToken(copy(body = prev, closer = null), stream)

            val next = body.withRef { copy(start = start + split) }
            stream.insert(copy(body = next, opener = null))
        }
    }.let {
        split >= 0
    }
}

private class SwapBuilder(stub: BlockToken.Code, parent: BlockTokenBuilder) : CodeBuilder(stub, parent) {
    // Nested definitions are kept as unparsed leaves and reported instead of being processed
    override fun acceptChild(builder: BlockTokenBuilder, stream: FileTokenStream) {
        if (builder is CodeBuilder) reportSwapNesting(builder)
        else super.acceptChild(builder, stream)
    }

    // A foreign closer inside a swap scope is ignored nesting, not a mismatch
    override fun isMismatchedCloser(next: BlockToken.Code): Boolean = false
}

private class ConditionBuilder(stub: BlockToken.Code, parent: BlockTokenBuilder) : CodeBuilder(stub, parent) {
}

private class ReplacementBuilder(stub: BlockToken.Code, parent: BlockTokenBuilder) : CodeBuilder(stub, parent) {
}

private fun CodeBuilder.terminatedBy(next: BlockToken.Code): Boolean =
    next.marker.type == marker.type && when {
        kind.isScoped -> next.definition.kind.isCloser
        kind.isOpen -> next.definition.kind.isExtension
        else -> false
    }

private fun BlockTokenBuilder.isNotBlank(): Boolean = when (this) {
    is ContentBuilder -> text.isNotBlank()
    is CommentBuilder -> text.isNotBlank()
    else -> true
}

// Issued when a code builder starting with } is not terminating any scope
private fun reportRootCloser(code: CodeBuilder): Unit = code.ref.report(LAYOUT_GROUP + "unmatched-root-closer") {
    label = "Unmatched ${code.name()} closer"
    info = "${code.title()} must have a corresponding `${code.marker.text} ... {` comment."

    // A preceding open-scope definition of the same marker was likely meant to be closed by this token.
    val candidate: CodeBuilder? = code.rootCloserCandidate()
    if (candidate != null) hint = buildString {
        appendLine("${candidate.title()} at this location may need to end with `{`:")
        append(candidate.ref.toUriString())
    }
}

// Locates an open-scope definition of the same marker - anywhere in the tree - that could have been closed instead
private fun CodeBuilder.rootCloserCandidate(): CodeBuilder? =
    (parent as? RootBuilder)?.findScopeCandidate(this)

// Depth-first search for the last (in document order) open scope this extra closer could have been meant to close
private fun ScopeBuilder.findScopeCandidate(closer: CodeBuilder): CodeBuilder? {
    var candidate: CodeBuilder? = null
    for (entry in entries) if (entry is CodeBuilder) {
        if (entry !== closer && entry.marker.type == closer.marker.type && entry.kind.isOpen) candidate = entry
        entry.findScopeCandidate(closer)?.let { candidate = it }
    }
    return candidate
}

// Issued when a scope is opened with { but never closed
private fun reportMissingClosingBracket(code: CodeBuilder): Unit = code.ref.report(LAYOUT_GROUP + "missing-scope-closer") {
    label = "Missing scope closer"
    info = "${code.title()} doesn't have a matching `${code.marker.text}}` closer."
    hint = "Add a `${code.marker.text}}` comment to close the scope opened here."
}

// Issues for every code builder inside a swap builder scope that is not its closer
context(parent: CodeBuilder)
private fun reportSwapNesting(code: CodeBuilder): Unit = code.ref.report(LAYOUT_GROUP + "definition-in-swap-scope") {
    severity = Severity.WARN
    label = "Ignored ${code.name()}"
    info = "${code.title()}s and other statements are not processed inside swap scopes."

    hint = buildString {
        appendLine("Swap scope is defined at this location:")
        append(parent.ref.toUriString())
    }
}

// Issued when a closer or extension token is following a scoped opener of an invalid type; i.e.
// `? if true { ... ~}` - ERR
// `? if true ... ~}` - OK because it closes the replacement before
// `$ id { ... ?}` - OK because it's reported by `reportSwapNesting`
context(parent: CodeBuilder)
private fun reportInvalidCloserType(code: CodeBuilder): Unit = code.ref.report(LAYOUT_GROUP + "mismatched-closer-type") {
    severity = Severity.WARN
    label = "Mismatched closer type"
    info = "${parent.title()} can't be closed with a ${code.name()} marker."

    hint = buildString {
        appendLine("${parent.title()} at this location needs to be closed with `${parent.marker.text}}`:")
        append(parent.ref.toUriString())
    }
}

// Issued when a lookup scope builder with a specific match (i.e. token.lookup != null) is not found.
// The [interrupter] that ended the search may be a nested definition or a closer of any enclosing scope.
private fun reportLookupStringNotFound(code: CodeBuilder, token: ScopeToken.Lookup, interrupter: BlockToken.Code?): Unit = code.ref.report(LAYOUT_GROUP + "lookup-not-found") {
    label = "Lookup string not found"
    info = "${code.title()} expected to find '${token.lookup?.unquote()}' in the text ahead."

    if (interrupter != null) hint = buildString {
        appendLine("${interrupter.title()} at this location stops the search:")
        append(interrupter.ref.toUriString())
    }
}

private val BlockTokenBuilder.ref: DocumentReference
    inline get() = stub.ref

private fun BlockTokenBuilder.title(): String =
    name().titlecase()

private fun BlockTokenBuilder.name(): String = when (this) {
    is ContentBuilder -> "text"
    is CommentBuilder -> "comment"
    is ConditionBuilder -> "condition"
    is SwapBuilder -> "swap"
    is ReplacementBuilder -> "replacement"
    is CodeBuilder -> "definition"
    is RootBuilder -> "root"
}

private fun BlockToken.Code.title(): String =
    name().titlecase()

private fun BlockToken.Code.name(): String = when (marker.typeValue) {
    Tokens.COND_MARK -> "condition"
    Tokens.SWAP_MARK -> "swap"
    Tokens.REPL_MARK -> "replacement"
    else -> "definition"
}

private fun BlockToken.Content.findLineSplit(immediate: Boolean): Int =
    leaf.text.findLineSplit(immediate)

private fun BlockToken.Comment.findLineSplit(immediate: Boolean): Int {
    val split = body.text.findLineSplit(immediate)
    return if (split >= 0 || body.text.isBlank() || '\n' !in opener?.text.orEmpty()) split else body.text.length
}

context(code: CodeBuilder)
private fun BlockToken.Content.findLookupSplit(): Int =
    findLookupSplit(code.definition.opener, leaf.text)

context(code: CodeBuilder)
private fun BlockToken.Comment.findLookupSplit(): Int =
    findLookupSplit(code.definition.opener, body.text)


private fun findLookupSplit(opener: ScopeToken?, text: String): Int {
    if (opener !is ScopeToken.Lookup) return -1

    val lookup: String? = opener.lookup?.unquote()
    return if (lookup == null) text.findWordSplit()
    else text.findLookupSplit(lookup, opener.plus != null)
}
