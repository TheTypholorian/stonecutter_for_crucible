@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.parser

import dev.kikugie.commons.takeAsOrNull
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.StitcherParser.Tokens
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.DocumentReference
import dev.kikugie.stitcher.document.issue.LAYOUT_GROUP
import dev.kikugie.stitcher.document.issue.ProblemReporter
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

private sealed class ScopeBuilder {
    abstract val stub: BlockToken
    abstract val parent: ScopeBuilder

    open fun accept(stream: FileTokenStream) {}
    open fun build(): BlockToken = stub

    override fun toString(): String = "${this::class.simpleName}($stub)"

    companion object : BlockToken.Visitor<ScopeBuilder, ScopeBuilder> {
        context(parent: ScopeBuilder)
        inline operator fun invoke(token: BlockToken): ScopeBuilder =
            token.accept(this, parent)

        override fun visitContent(content: BlockToken.Content, ctx: ScopeBuilder): ScopeBuilder =
            ContentBuilder(content, ctx)

        override fun visitComment(comment: BlockToken.Comment, ctx: ScopeBuilder): ScopeBuilder =
            CommentBuilder(comment, ctx)

        override fun visitRoot(root: BlockToken.Root, ctx: ScopeBuilder): ScopeBuilder =
            throw UnsupportedOperationException()

        override fun visitCode(code: BlockToken.Code, ctx: ScopeBuilder): ScopeBuilder = when (code.marker.typeValue) {
            Tokens.SWAP_MARK -> SwapBuilder(code, ctx)
            Tokens.COND_MARK -> ConditionBuilder(code, ctx)
            Tokens.REPL_MARK -> ReplacementBuilder(code, ctx)
            else -> throw IllegalArgumentException("Invalid definition type ${code.marker.type}")
        }
    }
}

private class ContentBuilder(override val stub: BlockToken.Content, override val parent: ScopeBuilder) : ScopeBuilder() {
    val text: String inline get() = stub.leaf.text
}

private class CommentBuilder(override val stub: BlockToken.Comment, override val parent: ScopeBuilder) : ScopeBuilder() {
    val text: String inline get() = stub.body.text
}

private class RootBuilder(document: Document) : ScopeBuilder() {
    override val stub: BlockToken.Root = BlockToken.Root(document, emptyList())
    override val parent: ScopeBuilder get() = this
    val entries: MutableList<ScopeBuilder> = ArrayList()

    override fun accept(stream: FileTokenStream) {
        while (true) acceptToken(stream.advance() ?: break, stream)
    }

    override fun build(): BlockToken.Root =
        stub.copy(scope = entries.map(ScopeBuilder::build))

    private fun acceptToken(token: BlockToken, stream: FileTokenStream) {
        val builder = ScopeBuilder(token)

        entries += builder
        builder.accept(stream)
    }
}

private sealed class CodeBuilder(override val stub: BlockToken.Code, override val parent: ScopeBuilder) : ScopeBuilder() {
    val entries: MutableList<ScopeBuilder> = ArrayList()

    val marker: LeafToken inline get() = stub.marker
    val definition: DefinitionToken inline get() = stub.definition
    val kind: Kind inline get() = definition.kind

    protected var scopeIsNotBlank: Boolean = false
    protected var satisfiedLookup: Boolean = false

    override fun build(): BlockToken =
        stub.copy(scope = entries.map(ScopeBuilder::build))

    override fun accept(stream: FileTokenStream): Unit = when (kind) {
        Kind.SCOPED_OPENER, Kind.SCOPED_EXTENSION -> acceptScoped(stream)
        Kind.LINE_OPENER, Kind.LINE_EXTENSION -> acceptLine(stream)
        Kind.LOOKUP_OPENER, Kind.LOOKUP_EXTENSION -> acceptLookup(stream)
        Kind.CLOSER, Kind.INDEPENDENT -> {}
    }

    protected fun acceptScoped(stream: FileTokenStream) {
        while (true) when (val next: BlockToken = stream.peek(1) ?: break) {
            is BlockToken.Code if (next.marker.type == marker.type && next.definition.kind.isCloser) -> break
            else -> acceptToken(next, stream)
        }
    }

    protected fun acceptLine(stream: FileTokenStream) {
        while (true) when (val next: BlockToken = stream.peek(1) ?: break) {
            is BlockToken.Content -> next.findLineSplit(scopeIsNotBlank).let {
                acceptSplitContent(next, stream, it)
                if (it >= 0) break
            }

            is BlockToken.Comment -> next.findLineSplit(scopeIsNotBlank).let {
                acceptSplitComment(next, stream, it)
                if (it >= 0) break
            }

            else -> TODO()
        }
    }

    protected fun acceptLookup(stream: FileTokenStream) {
        while (true) when (val next: BlockToken = stream.peek(1) ?: break) {
            is BlockToken.Content -> next.findLookupSplit().let {
                acceptSplitContent(next, stream, it)
                if (it >= 0) break
            }

            is BlockToken.Comment -> next.findLookupSplit().let {
                acceptSplitComment(next, stream, it)
                if (it >= 0) break
            }

            else -> TODO()
        }
    }

    protected fun acceptToken(token: BlockToken, stream: FileTokenStream) {
        val builder = ScopeBuilder(token)
        entries += builder
        scopeIsNotBlank = scopeIsNotBlank || when (builder) {
            is ContentBuilder -> builder.text.isNotBlank()
            is CommentBuilder -> builder.text.isNotBlank()
            else -> true
        }

        stream.consume()
        builder.accept(stream)
    }

    private fun acceptSplitContent(token: BlockToken.Content, stream: FileTokenStream, split: Int): Unit = when (split) {
        0 -> Unit
        AntlrToken.EOF -> acceptToken(token, stream)
        token.leaf.length -> acceptToken(token, stream).also { satisfiedLookup = true }
        else -> with(token) {
            val prev = leaf.withRef { copy(end = start + split) }
            acceptToken(copy(leaf = prev), stream)

            val next = leaf.withRef { copy(start = start + split) }
            stream.insert(copy(leaf = next))
        }
    }

    private fun acceptSplitComment(token: BlockToken.Comment, stream: FileTokenStream, split: Int): Unit = when (split) {
        0 -> Unit
        AntlrToken.EOF -> acceptToken(token, stream)
        token.body.length -> acceptToken(token, stream).also { satisfiedLookup = true }
        else -> with(token) {
            val prev = body.withRef { copy(end = start + split) }
            acceptToken(copy(body = prev, closer = null), stream)

            val next = body.withRef { copy(start = start + split) }
            stream.insert(copy(body = next, opener = null))
        }
    }
}

private class SwapBuilder(stub: BlockToken.Code, parent: ScopeBuilder) : CodeBuilder(stub, parent) {
}

private class ConditionBuilder(stub: BlockToken.Code, parent: ScopeBuilder) : CodeBuilder(stub, parent) {
}

private class ReplacementBuilder(stub: BlockToken.Code, parent: ScopeBuilder) : CodeBuilder(stub, parent) {
}

private fun reportRootCloser(code: CodeBuilder): Unit = code.ref.report(LAYOUT_GROUP + "unmatched-root-closer") {
    label = "Unmatched ${code.name()} closer"
    info = "${code.title()} must have a corresponding `${code.marker.text} ... {` comment."
}

private val ScopeBuilder.ref: DocumentReference
    inline get() = stub.ref

private fun ScopeBuilder.title(): String =
    name().titlecase()

private fun ScopeBuilder.name(): String = when (this) {
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
