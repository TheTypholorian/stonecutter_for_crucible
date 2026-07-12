package dev.kikugie.stitcher.transformer

import dev.kikugie.stitcher.StitcherParameters
import dev.kikugie.stitcher.antlr.StitcherErrorListener
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.InjectedDocument
import dev.kikugie.stitcher.document.SyntheticDocument
import dev.kikugie.stitcher.document.fullText
import dev.kikugie.stitcher.issue.runCatching
import dev.kikugie.stitcher.parser.FileAstParser
import dev.kikugie.stitcher.parser.FileTokenStream
import dev.kikugie.stitcher.replacement.template.findInsertionPlaces
import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.NativeStitcherType.COMMENT_CLOSE
import dev.kikugie.stitcher.token.NativeStitcherType.COMMENT_OPEN
import dev.kikugie.stitcher.token.StitcherType
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.DefinitionToken.Visitor
import dev.kikugie.stitcher.token.impl.LeafToken
import dev.kikugie.stitcher.util.errorListener
import dev.kikugie.stitcher.util.id
import dev.kikugie.stitcher.util.unquote
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import kotlin.io.path.absolutePathString

class BlockUnwrapper(val params: StitcherParameters) : BlockToken.Visitor<BlockToken, Nothing?> {
    private val unwrapper: ScopeUnwrapper = ScopeUnwrapper(params, ExpressionChecker(params.vars))

    override fun visitContent(content: BlockToken.Content, ctx: Nothing?): BlockToken = content
    override fun visitComment(comment: BlockToken.Comment, ctx: Nothing?): BlockToken = comment

    override fun visitRoot(root: BlockToken.Root, ctx: Nothing?): BlockToken =
        root.copy(scope = root.scope.map { it.accept(this, ctx) })

    override fun visitCode(code: BlockToken.Code, ctx: Nothing?): BlockToken =
        code.copy(scope = code.definition.accept(unwrapper, code))
}

private class ScopeUnwrapper(val params: StitcherParameters, val checker: ExpressionChecker) : Visitor<ScopeUnwrapper.BlockTokenList, BlockToken.Code> {
    typealias BlockTokenList = List<BlockToken>

    override fun visitSwapLocal(swap: DefinitionToken.SwapLocal, ctx: BlockToken.Code): BlockTokenList {
        if (isFaulted(ctx, swap)) return ctx.scope

        val variant = swap.entries.first { it.expression?.accept(ExpressionEvaluator, params.vars) ?: true }
        return processTemplate(variant.literal.unquote(), emptyList())
    }

    override fun visitSwapOpener(swap: DefinitionToken.SwapOpener, ctx: BlockToken.Code): BlockTokenList {
        if (isFaulted(ctx, swap)) return ctx.scope

        return processTemplate(params.vars.swaps[swap.id]!!, swap.arguments)
    }

    override fun visitSwapCloser(swap: DefinitionToken.SwapCloser, ctx: BlockToken.Code): BlockTokenList =
        emptyList()

    override fun visitConditionOpener(cond: DefinitionToken.ConditionOpener, ctx: BlockToken.Code): BlockTokenList {
        isFaulted(ctx, cond)
        return if (isCommented(ctx.scope)) uncommentScope(ctx) else ctx.scope
    }

    override fun visitConditionExtension(cond: DefinitionToken.ConditionExtension, ctx: BlockToken.Code): BlockTokenList {
        isFaulted(ctx, cond)
        return if (isCommented(ctx.scope)) uncommentScope(ctx) else ctx.scope
    }

    override fun visitConditionCloser(cond: DefinitionToken.ConditionCloser, ctx: BlockToken.Code): BlockTokenList =
        emptyList()

    override fun visitReplacementToggle(repl: DefinitionToken.ReplacementToggle, ctx: BlockToken.Code): BlockTokenList {
        isFaulted(ctx, repl)
        return emptyList()
    }

    override fun visitReplacementLocal(repl: DefinitionToken.ReplacementLocal, ctx: BlockToken.Code): BlockTokenList {
        isFaulted(ctx, repl)
        return ctx.scope
    }

    override fun visitReplacementCloser(repl: DefinitionToken.ReplacementCloser, ctx: BlockToken.Code): BlockTokenList =
        emptyList()

    private fun isFaulted(host: BlockToken.Code, def: DefinitionToken): Boolean =
        host.document.runCatching { def.accept(checker, this) }.isSuccess

    private fun isCommented(scope: BlockTokenList): Boolean = scope.isNotEmpty() && scope.all {
        it is BlockToken.Comment || it is BlockToken.Content && it.isBlank()
    }

    private fun processTemplate(template: String, tokens: List<LeafToken>): BlockTokenList {
        if (tokens.isEmpty()) return wrapTemplate(template)

        val builder = StringBuilder(template)
        var offset = 0
        val insertions = tokens.map(LeafToken::unquote)
        for (place in template.findInsertionPlaces())
            offset += place.apply(builder, offset) { insertions[it - 1] }
        return wrapTemplate(builder.toString())
    }

    private fun wrapTemplate(template: String): BlockTokenList {
        val document = SyntheticDocument(template)
        val token = LeafToken(document, NativeStitcherType.CONTENT, 0, template.length).apply { text = template }
        return listOf(BlockToken.Content(token))
    }

    private fun uncommentScope(code: BlockToken.Code): BlockTokenList {
        val document = InjectedDocument(code.document, code.scope.first().start, code.scope.last().end)
        params.uncommenter.uncomment(document.tracker, buildTokenSequence(document, code.scope))

        return processInjected(document, shallow = false)
    }

    private fun buildTokenSequence(document: InjectedDocument, scope: BlockTokenList): Sequence<LeafToken> = sequence {
        for (it in scope) when (it) {
            is BlockToken.Content -> {
                yield(it.leaf.copyWith(document))
            }

            is BlockToken.Comment -> {
                yield(it.opener ?: it.body.copyWith(document, COMMENT_OPEN, end = it.body.start))
                yield(it.body.copyWith(document))
                yield(it.opener ?: it.body.copyWith(document, COMMENT_CLOSE, start = it.body.start))
            }

            else -> error("Unreachable")
        }
    }

    private fun LeafToken.copyWith(document: InjectedDocument, type: StitcherType = this.type, start: Int = this.start, end: Int = this.end): LeafToken =
        LeafToken(document, type, start - document.start, end - document.start)

    private fun processInjected(document: Document, shallow: Boolean): BlockTokenList {
        val input = CharStreams.fromString(document.fullText(), document.path.absolutePathString())
        val scanner = params.scanner.create(input, document)
            .apply { scanner.errorListener = StitcherErrorListener(document) }
        val stream = FileTokenStream(document, CommonTokenStream(scanner))

        return if (shallow) buildList { stream.forEach(::add) }
        else FileAstParser.parse(stream).scope
    }
}
