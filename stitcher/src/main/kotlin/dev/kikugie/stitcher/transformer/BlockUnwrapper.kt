@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.transformer

import dev.kikugie.commons.then
import dev.kikugie.stitcher.StitcherParameters
import dev.kikugie.stitcher.antlr.StitcherErrorListener
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.InjectedDocument
import dev.kikugie.stitcher.document.fullText
import dev.kikugie.stitcher.issue.runCatching
import dev.kikugie.stitcher.parser.FileAstParser
import dev.kikugie.stitcher.parser.FileTokenStream
import dev.kikugie.stitcher.token.NativeStitcherType.COMMENT_CLOSE
import dev.kikugie.stitcher.token.NativeStitcherType.COMMENT_OPEN
import dev.kikugie.stitcher.token.StitcherType
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.LeafToken
import dev.kikugie.stitcher.util.errorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import kotlin.io.path.absolutePathString

class BlockUnwrapper(val params: StitcherParameters)
    : BlockToken.Visitor<BlockToken, Nothing?>, DefinitionToken.Visitor<BlockToken.Code, BlockToken.Code> {
    private val checker: ExpressionChecker = ExpressionChecker(params.vars)

    override fun visitContent(content: BlockToken.Content, ctx: Nothing?): BlockToken = content
    override fun visitComment(comment: BlockToken.Comment, ctx: Nothing?): BlockToken = comment

    override fun visitRoot(root: BlockToken.Root, ctx: Nothing?): BlockToken =
        root.copy(scope = root.scope.map { it.accept(this, ctx) })

    override fun visitCode(code: BlockToken.Code, ctx: Nothing?): BlockToken =
        code.definition.accept(this, code)

    override fun visitSwapLocal(swap: DefinitionToken.SwapLocal, ctx: BlockToken.Code): BlockToken.Code =
        checkFaulted(ctx, swap) then ctx

    override fun visitSwapOpener(swap: DefinitionToken.SwapOpener, ctx: BlockToken.Code): BlockToken.Code =
        checkFaulted(ctx, swap) then ctx

    override fun visitSwapCloser(swap: DefinitionToken.SwapCloser, ctx: BlockToken.Code): BlockToken.Code =
        ctx

    override fun visitConditionOpener(cond: DefinitionToken.ConditionOpener, ctx: BlockToken.Code): BlockToken.Code =
        checkFaulted(ctx, cond) then uncomment(ctx)

    override fun visitConditionExtension(cond: DefinitionToken.ConditionExtension, ctx: BlockToken.Code): BlockToken.Code =
        checkFaulted(ctx, cond) then uncomment(ctx)

    override fun visitConditionCloser(cond: DefinitionToken.ConditionCloser, ctx: BlockToken.Code): BlockToken.Code =
        ctx

    override fun visitReplacementToggle(repl: DefinitionToken.ReplacementToggle, ctx: BlockToken.Code): BlockToken.Code =
        checkFaulted(ctx, repl) then ctx

    override fun visitReplacementLocal(repl: DefinitionToken.ReplacementLocal, ctx: BlockToken.Code): BlockToken.Code =
        checkFaulted(ctx, repl) then reprocess(ctx)

    override fun visitReplacementCloser(repl: DefinitionToken.ReplacementCloser, ctx: BlockToken.Code): BlockToken.Code =
        ctx

    private fun checkFaulted(host: BlockToken.Code, def: DefinitionToken): Boolean =
        host.document.runCatching { def.accept(checker, this) }.isSuccess

    private inline fun reprocess(code: BlockToken.Code): BlockToken.Code =
        code.copy(scope = code.scope.map { it.accept(this@BlockUnwrapper, null) })

    private fun uncomment(code: BlockToken.Code): BlockToken.Code {
        if (!isCommented(code)) return reprocess(code)

        val document = InjectedDocument(code.document, code.scope.first().start, code.scope.last().end)
        params.uncommenter.uncomment(document.tracker, buildTokenSequence(document, code.scope))

        val entries = processInjected(document, shallow = false)
            .map { it.accept(this@BlockUnwrapper, null) }
        return code.copy(scope = entries)
    }

    private fun buildTokenSequence(document: InjectedDocument, scope: List<BlockToken>): Sequence<LeafToken> = sequence {
        for (it in scope) when (it) {
            is BlockToken.Content -> {
                yield(it.leaf.injected(document))
            }

            is BlockToken.Comment -> {
                yield(it.opener ?: it.body.injected(document, COMMENT_OPEN, end = it.body.start))
                yield(it.body.injected(document))
                yield(it.opener ?: it.body.injected(document, COMMENT_CLOSE, start = it.body.start))
            }

            else -> error("Unreachable")
        }
    }

    private fun processInjected(document: Document, shallow: Boolean): List<BlockToken> {
        val input = CharStreams.fromString(document.fullText(), document.path.absolutePathString())
        val scanner = params.scanner.create(input, document)
            .apply { scanner.errorListener = StitcherErrorListener(document) }
        val stream = FileTokenStream(document, CommonTokenStream(scanner))

        return if (shallow) buildList { stream.forEach(::add) }
        else FileAstParser.parse(stream).scope
    }
}

private fun isCommented(code: BlockToken.Code): Boolean = code.scope.isNotEmpty() && code.scope.all {
    it is BlockToken.Comment || it is BlockToken.Content && it.isBlank()
}

private fun LeafToken.injected(document: InjectedDocument, type: StitcherType = this.type, start: Int = this.start, end: Int = this.end): LeafToken =
    LeafToken(document, type, start - document.start, end - document.start)
