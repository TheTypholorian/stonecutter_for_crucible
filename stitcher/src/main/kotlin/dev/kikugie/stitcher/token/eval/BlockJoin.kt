package dev.kikugie.stitcher.token.eval

import dev.kikugie.stitcher.token.impl.BlockToken

fun BlockToken.join(): String = buildString { joinTo(this) }
fun BlockToken.joinTo(builder: StringBuilder) = accept(BlockToStringVisitor(builder))

fun Iterable<BlockToken>.join(): String = buildString { joinTo(this) }
fun Iterable<BlockToken>.joinTo(builder: StringBuilder) {
    val visitor = BlockToStringVisitor(builder)
    for (it in this) it.accept(visitor)
}

private class BlockToStringVisitor(val builder: StringBuilder) : BlockToken.Visitor<Unit> {
    override fun visitContent(content: BlockToken.Content) {
        builder.append(content.leaf.text)
    }

    override fun visitComment(comment: BlockToken.Comment) {
        comment.opener?.text?.let(builder::append)
        builder.append(comment.body.text)
        comment.closer?.text?.let(builder::append)
    }

    override fun visitCode(code: BlockToken.Code) {
        code.host.accept(this)
        code.scope.forEach { it.accept(this) }
    }

    override fun visitRoot(root: BlockToken.Root) {
        root.scope.forEach { it.accept(this) }
    }
}