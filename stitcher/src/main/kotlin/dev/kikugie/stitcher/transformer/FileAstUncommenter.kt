package dev.kikugie.stitcher.transformer

import dev.kikugie.antlr.util.toSequence
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
import dev.kikugie.stitcher.token.type.RootScopeState
import dev.kikugie.stitcher.util.LINE_BREAKS
import dev.kikugie.commons.ranges.length
import dev.kikugie.commons.collections.findIsInstance
import dev.kikugie.stitcher.util.parse
import dev.kikugie.stitcher.util.tokenize

class FileAstUncommenter(val params: StitcherParameters) : Visitor<BlockToken, Nothing?>, DefinitionToken.Visitor<Code, Code> {
    override fun visitContent(content: Content, ctx: Nothing?): BlockToken = content
    override fun visitComment(comment: Comment, ctx: Nothing?): BlockToken = reopenDanglingComment(comment)
    override fun visitCode(code: Code, ctx: Nothing?): BlockToken = code.definition.accept(this, code)
    override fun visitRoot(root: Root, ctx: Nothing?): BlockToken = root.copy(scope = uncommentRoot(root).map { it.accept(this, ctx) })
    override fun visitSwapLocal(swap: DefinitionToken.SwapLocal, ctx: Code): Code = ctx
    override fun visitSwapOpener(swap: DefinitionToken.SwapOpener, ctx: Code): Code = ctx
    override fun visitSwapCloser(swap: DefinitionToken.SwapCloser, ctx: Code): Code = ctx
    override fun visitConditionOpener(cond: DefinitionToken.ConditionOpener, ctx: Code): Code = uncommentCode(ctx)
    override fun visitConditionExtension(cond: DefinitionToken.ConditionExtension, ctx: Code): Code = uncommentCode(ctx)
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
            params.commenter.comment(
                params.flags, this, tokenize(spliced, params.scanner).toSequence()
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

    private fun isCommented(scope: List<BlockToken>): Boolean =
        scope.isNotEmpty() && scope.all { it is Comment || it is Content && it.leaf.text.isBlank() }

    private fun reprocess(code: Code, scope: List<BlockToken> = code.scope): Code =
        code.copy(scope = scope.map { it.accept(this, null) })

    private fun isDisabled(scope: List<BlockToken>): Boolean {
        if (!isCommented(scope)) return false
        val first: Comment = scope.findIsInstance<Comment>() ?: return false
        return first.body.text.substringBefore('\n').trimEnd() == RootScopeState.MARKER
    }

    private fun uncommentRoot(root: Root): List<BlockToken> {
        if (!isDisabled(root.scope)) return root.scope

        val uncommented: Document = uncommentDocument(root.scope)
        val marker: Int = uncommented.indexOf(RootScopeState.MARKER)
        val lineStart: Int = uncommented.lastIndexOf('\n', marker) + 1
        val lineEnd: Int = uncommented.indexOf('\n', marker).let { if (it < 0) uncommented.length else it + 1 }
        val stripped = ModificationTracker(uncommented, 0, uncommented.length).run {
            delete(lineStart, lineEnd)
            InjectedDocument(this)
        }
        return parse(stripped, params.scanner).scope
    }

    private fun uncommentCode(code: Code): Code =
        if (isCommented(code.scope)) reprocess(code, uncommentScope(code.scope)) else reprocess(code)

    private fun uncommentScope(scope: List<BlockToken>): List<BlockToken> =
        parse(uncommentDocument(scope), params.scanner).scope

    private fun uncommentDocument(scope: List<BlockToken>): Document = ModificationTracker(scope.run { first().ref join last().ref }).run {
        params.uncommenter.uncomment(params.flags, this, toSequence(document, scope))
        InjectedDocument(this)
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
