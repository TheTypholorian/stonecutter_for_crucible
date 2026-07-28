@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.parser

import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.StitcherParser.Tokens
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.DocumentReference
import dev.kikugie.stitcher.document.issue.LAYOUT_GROUP
import dev.kikugie.stitcher.document.issue.report
import dev.kikugie.stitcher.token.BlockToken
import dev.kikugie.stitcher.token.DefinitionToken
import dev.kikugie.stitcher.token.DefinitionToken.Kind
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.token.ScopeToken
import dev.kikugie.stitcher.token.typeValue
import dev.kikugie.stitcher.token.unquote
import dev.kikugie.stitcher.token.withRef
import dev.kikugie.stitcher.util.findLineSplit
import dev.kikugie.stitcher.util.findLookupSplit
import dev.kikugie.stitcher.util.findWordSplit
import dev.kikugie.stitcher.util.length
import dev.kikugie.stitcher.util.titlecase

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

    protected var scopeIsNotBlank: Boolean = false

    protected fun acceptToken(token: BlockToken, stream: FileTokenStream) {
        val builder = BlockTokenBuilder(token)
        entries += builder
        scopeIsNotBlank = scopeIsNotBlank || builder.isNotBlank()

        stream.consume()
        acceptChild(builder, stream)
    }

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
        while (true) acceptToken(stream.peek(1) ?: break, stream)
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
        while (true) when (val next: BlockToken = stream.peek(1) ?: break) {
            is BlockToken.Code if terminatedBy(next) -> break
            else -> acceptToken(next, stream)
        }
    }

    protected fun acceptLine(stream: FileTokenStream) {
        while (true) when (val next: BlockToken = stream.peek(1) ?: break) {
            is BlockToken.Content -> if (acceptSplit(next, stream, next.findLineSplit(scopeIsNotBlank))) break
            is BlockToken.Comment -> if (acceptSplit(next, stream, next.findLineSplit(scopeIsNotBlank))) break
            is BlockToken.Code if terminatedBy(next) -> break
            // A nested definition owns the rest of the line and possibly more, so this scope ends with it.
            is BlockToken.Code -> { acceptToken(next, stream); break }
            is BlockToken.Root -> break
        }
    }

    protected fun acceptLookup(stream: FileTokenStream) {
        while (true) when (val next: BlockToken = stream.peek(1) ?: break) {
            is BlockToken.Content -> if (acceptSplit(next, stream, next.findLookupSplit())) break
            is BlockToken.Comment -> if (acceptSplit(next, stream, next.findLookupSplit())) break
            is BlockToken.Code if terminatedBy(next) -> break
            // A lookup keeps searching for its match across nested definitions.
            is BlockToken.Code -> acceptToken(next, stream)
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
        token.leaf.length -> acceptToken(token, stream).also { satisfiedLookup = true }
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
        token.body.length -> acceptToken(token, stream).also { satisfiedLookup = true }
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
    override fun acceptChild(builder: BlockTokenBuilder, stream: FileTokenStream) {
        if (builder !is CodeBuilder) super.acceptChild(builder, stream)
    }
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

private fun reportRootCloser(code: CodeBuilder): Unit = code.ref.report(LAYOUT_GROUP + "unmatched-root-closer") {
    label = "Unmatched ${code.name()} closer"
    info = "${code.title()} must have a corresponding `${code.marker.text} ... {` comment."
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
