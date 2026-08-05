package dev.kikugie.stitcher.transformer

import dev.kikugie.stitcher.StitcherParameters
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.InjectedDocument
import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.document.join
import dev.kikugie.stitcher.document.reference
import dev.kikugie.stitcher.token.BlockToken
import dev.kikugie.stitcher.token.BlockToken.*
import dev.kikugie.stitcher.token.DefinitionToken
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.token.toLeaf
import dev.kikugie.stitcher.util.LINE_BREAKS
import dev.kikugie.stitcher.util.parse
import dev.kikugie.stitcher.util.tokenize
import dev.kikugie.antlr.util.toSequence
import dev.kikugie.stitcher.util.length

class FileAstUncommenter(val params: StitcherParameters) : BlockToken.Visitor<BlockToken, Nothing?>, DefinitionToken.Visitor<Code, Code> {
    override fun visitContent(content: Content, ctx: Nothing?): BlockToken = content
    override fun visitComment(comment: Comment, ctx: Nothing?): BlockToken = reopenDanglingComment(comment)
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

    private fun reopenDanglingComment(comment: Comment): BlockToken {
        if (comment.opener != null || comment.closer == null) return comment

        val spliced = InjectedDocument(ModificationTracker(comment.ref))
        val bodyLength = comment.body.length
        var lead = 0
        while (lead < bodyLength && spliced[lead] in LINE_BREAKS) lead++

        val body = ModificationTracker(spliced, lead, bodyLength).run {
            params.commenter.comment(this, tokenize(spliced, params.scanner).toSequence()
                .map { it.toLeaf(spliced) }
                .filter { it.start >= lead && it.end <= bodyLength })
            InjectedDocument(this).toString()
        }

        val reopened = ModificationTracker(spliced, 0, spliced.length).run {
            replace(lead, spliced.length, body)
            InjectedDocument(this)
        }
        return Content(reopened.reference())
    }

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
