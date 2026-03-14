package dev.kikugie.stitcher.transformer

import dev.kikugie.stitcher.replacement.executor.ReplacementExecutor
import dev.kikugie.stitcher.replacement.executor.replaceIn
import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.eval.joinTo
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.LeafToken

internal class BlockCollector(val replacer: ReplacementExecutor, val list: MutableList<BlockToken> = mutableListOf()) : BlockToken.Visitor<Unit> {
    private var stub: ContentBlockStub? = null
    override fun visitContent(content: BlockToken.Content) {
        stub += content
    }

    override fun visitComment(comment: BlockToken.Comment) {
        stub += comment
    }

    override fun visitCode(code: BlockToken.Code) {
        list += code
    }

    override fun visitRoot(root: BlockToken.Root) {
        for (it in root.scope) it.accept(this)
    }

    fun submit() {
        stub?.build(replacer)?.let(list::add)
        stub = null
    }
}

private class ContentBlockStub(var start: Int, var end: Int, var text: StringBuilder) {
    constructor(token: BlockToken.Content) : this(token.start, token.end, StringBuilder(token.leaf.text))
    constructor(token: BlockToken.Comment) : this(token.start, token.end, StringBuilder().apply { token.joinTo(this) })

    fun merge(other: BlockToken) {
        end = other.end
        other.joinTo(text)
    }

    fun build(replacer: ReplacementExecutor): BlockToken.Content {
        replacer.replaceIn(text)
        val leaf = LeafToken(NativeStitcherType.CONTENT, start, end, text.toString())
        return BlockToken.Content(leaf)
    }
}

private operator fun ContentBlockStub?.plus(other: BlockToken.Content): ContentBlockStub =
    this?.apply { merge(other) } ?: ContentBlockStub(other)

private operator fun ContentBlockStub?.plus(other: BlockToken.Comment): ContentBlockStub =
    this?.apply { merge(other) } ?: ContentBlockStub(other)
