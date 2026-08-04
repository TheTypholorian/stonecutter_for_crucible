package dev.kikugie.stitcher.transformer

import dev.kikugie.stitcher.StitcherParameters
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.InjectedDocument
import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.document.join
import dev.kikugie.stitcher.token.BlockToken
import dev.kikugie.stitcher.token.BlockToken.*
import dev.kikugie.stitcher.token.DefinitionToken
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.util.parse

class FileAstUncommenter(val params: StitcherParameters) : BlockToken.Visitor<BlockToken, Nothing?>, DefinitionToken.Visitor<Code, Code> {
    override fun visitContent(content: Content, ctx: Nothing?): BlockToken = content
    override fun visitComment(comment: Comment, ctx: Nothing?): BlockToken = comment
    override fun visitCode(code: Code, ctx: Nothing?): BlockToken = code.definition.accept(this, code)
    override fun visitRoot(root: Root, ctx: Nothing?): BlockToken = root.copy(scope = root.scope.map { it.accept(this, ctx) })
    override fun visitSwapLocal(swap: DefinitionToken.SwapLocal, ctx: Code): Code = ctx
    override fun visitSwapOpener(swap: DefinitionToken.SwapOpener, ctx: Code): Code = ctx
    override fun visitSwapCloser(swap: DefinitionToken.SwapCloser, ctx: Code): Code = ctx
    override fun visitConditionOpener(cond: DefinitionToken.ConditionOpener, ctx: Code): Code = uncomment(ctx)
    override fun visitConditionExtension(cond: DefinitionToken.ConditionExtension, ctx: Code): Code = uncomment(ctx)
    override fun visitConditionCloser(cond: DefinitionToken.ConditionCloser, ctx: Code): Code = ctx
    override fun visitReplacementToggle(repl: DefinitionToken.ReplacementToggle, ctx: Code): Code = ctx
    override fun visitReplacementLocal(repl: DefinitionToken.ReplacementLocal, ctx: Code): Code = reprocess(ctx)
    override fun visitReplacementCloser(repl: DefinitionToken.ReplacementCloser, ctx: Code): Code = ctx

    private fun isCommented(code: Code): Boolean =
        code.scope.isNotEmpty() && code.scope.all { it is Comment || it is Content && it.leaf.text.isBlank() }

    private fun reprocess(code: Code, scope: List<BlockToken> = code.scope): Code =
        code.copy(scope = scope.map { it.accept(this, null) })

    private fun uncomment(code: Code): Code {
        if (!isCommented(code)) return reprocess(code)

        val document = ModificationTracker(code.scope.run { first().ref join last().ref }).run {
            params.uncommenter.uncomment(this, toSequence(document, code.scope))
            InjectedDocument(this)
        }
        return reprocess(code, parse(document, params.scanner).scope)
    }

    private fun toSequence(document: Document, tokens: List<BlockToken>): Sequence<LeafToken> = tokens.asSequence().flatMap {
        check(it.ref.document == document) { "Mismatched document for $it" }
        when (it) {
            is Content -> listOf(it.leaf)
            is Comment -> listOfNotNull(it.opener, it.body, it.closer)
            else -> emptyList()
        }
    }
}
