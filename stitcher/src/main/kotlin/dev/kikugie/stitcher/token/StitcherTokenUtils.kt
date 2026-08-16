@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.token

import dev.kikugie.antlr.util.endIndex
import dev.kikugie.semver.data.Version
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.StitcherLexer.Tokens
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.DocumentReference
import dev.kikugie.stitcher.document.appendTo
import dev.kikugie.stitcher.document.reference
import dev.kikugie.stitcher.token.type.StitcherTokenType
import dev.kikugie.stitcher.util.unescapeQuoted
import org.antlr.v4.runtime.tree.TerminalNode

val StitcherToken?.typeValue: Int
    inline get() = this?.type?.value ?: AntlrToken.EOF

val BlockToken.Comment.openerStr: String
    inline get() = opener?.text.orEmpty()

val BlockToken.Comment.closerStr: String
    inline get() = closer?.text.orEmpty()

val DefinitionToken.SwapOpener.id: String
    inline get() = identifier.text

val DefinitionToken.SwapOpener.args: Sequence<String>
    inline get() = arguments.asSequence().map(LeafToken::unquote)

val DefinitionToken.ReplacementToggle.Entry.id: String
    inline get() = identifier.text

// Adding nodes to the trie is irreversible, so they need to be disabled later
val DefinitionToken.ReplacementLocal.Entry.id: String
    get() = "%synthetic% ${source.text} -> ${target.text}"

val ScopeToken.Named.id: String?
    inline get() = identifier.takeIf { it.typeValue != Tokens.UNDERSCORE }?.text

val ExpressionToken.Constant.text: String
    inline get() = value.text

val ExpressionToken.Assignment.id: String
    inline get() = target?.text.orEmpty()

fun TerminalNode.toLeaf(doc: Document, type: StitcherTokenType = StitcherTokenType.of(symbol.type)): LeafToken =
    symbol.toLeaf(doc, type)

fun AntlrToken.toLeaf(doc: Document, type: StitcherTokenType = StitcherTokenType.of(this.type)): LeafToken =
    LeafToken(type, doc.reference(startIndex, endIndex))

fun LeafToken.unquote(): String = when(type.value) {
    Tokens.QUOTED -> text.removeSurrounding("'").unescapeQuoted()
    else -> text
}

inline fun LeafToken.withRef(builder: DocumentReference.() -> DocumentReference): LeafToken =
    copy(ref = ref.builder())

fun ExpressionToken.Assignment.eval(version: Version): Boolean =
    predicates.all { it.operator(version, it.version) }

fun BlockToken.appendTo(builder: Appendable): Unit =
    accept(BlockTokenJoiner, builder)

fun BlockToken.toTreeString(): String = buildString {
    accept(BlockTokenPrettyPrinter, BlockTokenPrettyPrinter.Context(this, "", ""))
}

fun Iterable<BlockToken>.appendTo(builder: Appendable): Unit =
    forEach { it.accept(BlockTokenJoiner, builder) }

inline infix fun StitcherToken.neqtype(other: StitcherToken): Boolean =
    !eqtype(other)

inline infix fun StitcherToken.eqtype(other: StitcherToken): Boolean =
    type.value == other.type.value

private object BlockTokenJoiner : BlockToken.Visitor<Unit, Appendable> {
    override fun visitContent(content: BlockToken.Content, ctx: Appendable) =
        content.ref.appendTo(ctx)

    override fun visitComment(comment: BlockToken.Comment, ctx: Appendable) =
        comment.ref.appendTo(ctx)

    override fun visitCode(code: BlockToken.Code, ctx: Appendable) {
        code.ref.appendTo(ctx)
        appendScope(code.scope, ctx)
    }

    override fun visitRoot(root: BlockToken.Root, ctx: Appendable) {
        appendScope(root.scope, ctx)
    }

    private fun appendScope(tokens: List<BlockToken>, ctx: Appendable) {
        var document: Document? = null
        var start = 0
        var end = 0
        for (it in tokens) when (it) {
            is BlockToken.Content, is BlockToken.Comment -> it.ref.let { ref ->
                if (ref.document === document && ref.start == end) { end = ref.end; return@let }

                document?.appendTo(ctx, start, end)
                document = ref.document
                start = ref.start
                end = ref.end
            }

            else -> {
                document?.appendTo(ctx, start, end)
                it.accept(this, ctx)
                document = null
            }
        }
        document?.appendTo(ctx, start, end)
    }
}

private object BlockTokenPrettyPrinter : BlockToken.Visitor<Unit, BlockTokenPrettyPrinter.Context> {
    class Context(val builder: StringBuilder, val self: String, val childIndent: String)

    override fun visitContent(content: BlockToken.Content, ctx: Context) {
        ctx.line("Content: ${content.range}")
        ctx.text(content.ref.text, hasChildren = false)
    }

    override fun visitComment(comment: BlockToken.Comment, ctx: Context) {
        val flags = "${flag(comment.opener)}${flag(comment.body)}${flag(comment.closer)}"
        ctx.line("Comment: ${comment.range} $flags")
        ctx.text(comment.ref.text, hasChildren = false)
    }

    override fun visitCode(code: BlockToken.Code, ctx: Context) {
        ctx.line("Code/${code.definition::class.simpleName}: ${code.range} ${code.scope.size}x")
        ctx.text(code.ref.text, hasChildren = code.scope.isNotEmpty())
        ctx.children(code.scope)
    }

    override fun visitRoot(root: BlockToken.Root, ctx: Context) {
        ctx.line("Root: ${root.scope.size}x")
        ctx.children(root.scope)
    }

    private fun Context.line(text: String) {
        builder.append(self).append(text).append('\n')
    }

    private fun Context.text(text: String, hasChildren: Boolean) {
        if (text.isEmpty()) return
        val prefix = childIndent + if (hasChildren) "│   " else "    "
        "'$text'".lineSequence().forEach { builder.append(prefix).append(it).append('\n') }
    }

    private fun Context.children(scope: List<BlockToken>) {
        for ((index, child) in scope.withIndex()) {
            val last = index == scope.lastIndex
            val connector = if (last) "└── " else "├── "
            val continuation = if (last) "    " else "│   "
            child.accept(this@BlockTokenPrettyPrinter, Context(builder, childIndent + connector, childIndent + continuation))
        }
    }

    private val BlockToken.range: String get() = "${ref.start}..<${ref.end}"

    private fun flag(leaf: LeafToken?): Char = if (leaf != null) '+' else '-'
}
