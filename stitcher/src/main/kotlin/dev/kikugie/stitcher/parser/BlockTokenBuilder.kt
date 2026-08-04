@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.parser

import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.StitcherParser.Tokens
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.token.*
import dev.kikugie.stitcher.token.DefinitionToken.Kind
import dev.kikugie.stitcher.util.*

/**
 * Mutable counterpart of a [BlockToken] used while assembling the file AST.
 *
 * Builders only parse the token stream and record neutral facts about what happened
 * (see the flags on [CodeBuilder]); turning those facts into diagnostics is a separate
 * step performed by the layout reporter before [build] produces the immutable tree.
 */
internal sealed class BlockTokenBuilder {
    abstract val stub: BlockToken
    abstract val parent: BlockTokenBuilder

    open fun accept(stream: FileTokenStream) {}
    open fun build(): BlockToken = stub

    abstract fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T

    override fun toString(): String = "${this::class.simpleName}($stub)"

    interface Visitor<T, C> {
        fun visitContent(content: ContentBuilder, ctx: C): T
        fun visitComment(comment: CommentBuilder, ctx: C): T
        fun visitCode(code: CodeBuilder, ctx: C): T
        fun visitRoot(root: RootBuilder, ctx: C): T
    }

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

internal class ContentBuilder(override val stub: BlockToken.Content, override val parent: BlockTokenBuilder) : BlockTokenBuilder() {
    val text: String inline get() = stub.leaf.text

    override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitContent(this, ctx)
}

internal class CommentBuilder(override val stub: BlockToken.Comment, override val parent: BlockTokenBuilder) : BlockTokenBuilder() {
    val text: String inline get() = stub.body.text

    override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitComment(this, ctx)
}

internal sealed class ScopeBuilder : BlockTokenBuilder() {
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

internal class RootBuilder(document: Document) : ScopeBuilder() {
    override val stub: BlockToken.Root = BlockToken.Root(document, emptyList())
    override val parent: BlockTokenBuilder get() = this

    override fun accept(stream: FileTokenStream) {
        while (true) {
            // A closer reaching the top-level loop terminates nothing - matched closers are consumed by [acceptChild]
            val builder = acceptToken(stream.peek(1) ?: break, stream)
            if (builder is CodeBuilder && builder.kind.isCloser) builder.unmatchedCloser = true
        }
    }

    override fun build(): BlockToken.Root =
        stub.copy(scope = entries.map(BlockTokenBuilder::build))

    override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitRoot(this, ctx)
}

internal sealed class CodeBuilder(override val stub: BlockToken.Code, override val parent: BlockTokenBuilder) : ScopeBuilder() {
    val marker: LeafToken inline get() = stub.marker
    val definition: DefinitionToken inline get() = stub.definition
    val kind: Kind inline get() = definition.kind

    // Facts recorded during parsing and inspected by the layout reporter.
    var terminated: Boolean = false
    var satisfiedLookup: Boolean = false
    var lookupInterrupter: BlockToken.Code? = null
    var mismatchedCloser: CodeBuilder? = null
    var unmatchedCloser: Boolean = false

    override fun build(): BlockToken =
        stub.copy(scope = entries.map(BlockTokenBuilder::build))

    override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitCode(this, ctx)

    override fun accept(stream: FileTokenStream): Unit = when (kind) {
        Kind.SCOPED_OPENER, Kind.SCOPED_EXTENSION -> acceptScoped(stream)
        Kind.LINE_OPENER, Kind.LINE_EXTENSION -> acceptLine(stream).also { acceptChainGap(stream) }
        Kind.LOOKUP_OPENER, Kind.LOOKUP_EXTENSION -> acceptLookup(stream).also { acceptChainGap(stream) }
        Kind.CLOSER, Kind.INDEPENDENT -> {}
    }

    // Ends on the matching closer ([terminated]), a foreign closer ([mismatchedCloser]) or the stream's end (neither).
    protected fun acceptScoped(stream: FileTokenStream) {
        while (true) when (val next: BlockToken = stream.peek(1) ?: return) {
            is BlockToken.Code if terminatedBy(next) -> { terminated = true; break }
            is BlockToken.Code if isMismatchedCloser(next) -> { mismatchedCloser = acceptToken(next, stream) as CodeBuilder; break }
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
        while (true) when (val next: BlockToken = stream.peek(1) ?: break) {
            is BlockToken.Content -> if (acceptSplit(next, stream, next.findLookupSplit())) { satisfiedLookup = true; break }
            is BlockToken.Comment -> if (acceptSplit(next, stream, next.findLookupSplit())) { satisfiedLookup = true; break }
            is BlockToken.Code if terminatedBy(next) -> break
            // A closer belongs to an enclosing scope and stops the search without being consumed.
            is BlockToken.Code if next.definition.kind.isCloser -> { lookupInterrupter = next; break }
            // A lookup keeps searching for its match across nested definitions, but they may hide the match.
            is BlockToken.Code -> { lookupInterrupter = lookupInterrupter ?: next; acceptToken(next, stream) }
            is BlockToken.Root -> break
        }
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

internal class SwapBuilder(stub: BlockToken.Code, parent: BlockTokenBuilder) : CodeBuilder(stub, parent) {
    // Nested definitions are kept as unparsed leaves; the reporter flags them as ignored
    override fun acceptChild(builder: BlockTokenBuilder, stream: FileTokenStream) {
        if (builder !is CodeBuilder) super.acceptChild(builder, stream)
    }

    // A foreign closer inside a swap scope is ignored nesting, not a mismatch
    override fun isMismatchedCloser(next: BlockToken.Code): Boolean = false
}

internal class ConditionBuilder(stub: BlockToken.Code, parent: BlockTokenBuilder) : CodeBuilder(stub, parent)

internal class ReplacementBuilder(stub: BlockToken.Code, parent: BlockTokenBuilder) : CodeBuilder(stub, parent)

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
