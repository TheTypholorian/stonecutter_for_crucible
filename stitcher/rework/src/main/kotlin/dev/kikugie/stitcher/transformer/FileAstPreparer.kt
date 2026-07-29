package dev.kikugie.stitcher.transformer

import dev.kikugie.commons.then
import dev.kikugie.stitcher.StitcherParameters
import dev.kikugie.stitcher.antlr.StitcherErrorListener
import dev.kikugie.stitcher.antlr.errorListener
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.DocumentReference
import dev.kikugie.stitcher.document.InjectedDocument
import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.document.issue.runMarked
import dev.kikugie.stitcher.parser.FileAstParser
import dev.kikugie.stitcher.parser.FileTokenStream
import dev.kikugie.stitcher.token.BlockToken
import dev.kikugie.stitcher.token.BlockToken.Code
import dev.kikugie.stitcher.token.BlockToken.Comment
import dev.kikugie.stitcher.token.BlockToken.Content
import dev.kikugie.stitcher.token.BlockToken.Root
import dev.kikugie.stitcher.token.DefinitionToken
import dev.kikugie.stitcher.token.LeafToken
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import kotlin.io.path.absolutePathString
import kotlin.text.isBlank

class FileAstPreparer(val params: StitcherParameters) : BlockToken.Visitor<BlockToken, Nothing?>, DefinitionToken.Visitor<Code, Code> {
    private val context: ExpressionChecker.Context = ExpressionChecker.Context(params.vars)
    override fun visitContent(content: Content, ctx: Nothing?): BlockToken = content

    override fun visitComment(comment: Comment, ctx: Nothing?): BlockToken = comment

    override fun visitCode(code: Code, ctx: Nothing?): BlockToken =
        code.definition.accept(this, code)

    override fun visitRoot(root: Root, ctx: Nothing?): BlockToken =
        root.copy(scope = root.scope.map { it.accept(this, ctx) })

    override fun visitSwapLocal(swap: DefinitionToken.SwapLocal, ctx: Code): Code =
        checkFaulted(ctx, swap) then ctx

    override fun visitSwapOpener(swap: DefinitionToken.SwapOpener, ctx: Code): Code =
        checkFaulted(ctx, swap) then ctx

    override fun visitSwapCloser(swap: DefinitionToken.SwapCloser, ctx: Code): Code =
        ctx

    override fun visitConditionOpener(cond: DefinitionToken.ConditionOpener, ctx: Code): Code =
        checkFaulted(ctx, cond) then uncomment(ctx)

    override fun visitConditionExtension(cond: DefinitionToken.ConditionExtension, ctx: Code): Code =
        checkFaulted(ctx, cond) then uncomment(ctx)

    override fun visitConditionCloser(cond: DefinitionToken.ConditionCloser, ctx: Code): Code =
        ctx

    override fun visitReplacementToggle(repl: DefinitionToken.ReplacementToggle, ctx: Code): Code =
        checkFaulted(ctx, repl) then ctx

    override fun visitReplacementLocal(repl: DefinitionToken.ReplacementLocal, ctx: Code): Code =
        checkFaulted(ctx, repl) then reprocess(ctx)

    override fun visitReplacementCloser(repl: DefinitionToken.ReplacementCloser, ctx: Code): Code =
        ctx

    private fun checkFaulted(host: Code, def: DefinitionToken): Boolean =
        host.ref.document.runMarked({ def.accept(ExpressionChecker, context) }, { _, ok -> ok })

    private fun isCommented(code: Code): Boolean = code.scope.isNotEmpty() && code.scope.all {
        it is Comment || it is Content && it.leaf.text.isBlank()
    }

    private fun reprocess(code: Code, scope: List<BlockToken> = code.scope): Code =
        code.copy(scope = scope.map { it.accept(this, null) })

    private fun uncomment(code: Code): Code {
        if (!isCommented(code)) return reprocess(code)

        val reference = DocumentReference(code.ref.document, code.scope.first().ref.start, code.scope.last().ref.end)
        val tracker = ModificationTracker(reference).apply {
            val tokens = toSequence(document, code.scope)
            params.uncommenter.uncomment(this, tokens)
        }
        val document = InjectedDocument(reference, tracker)
        return reprocess(code, parseInjected(document))
    }

    private fun toSequence(document: Document, tokens: List<BlockToken>): Sequence<LeafToken> = tokens.asSequence().flatMap {
        check(it.ref.document == document) { "Mismatched document for $it" }
        when (it) {
            is Content -> listOf(it.leaf)
            is Comment -> listOfNotNull(it.opener, it.body, it.closer)
            else -> emptyList()
        }
    }

    private fun parseInjected(document: InjectedDocument): List<BlockToken> {
        val input = CharStreams.fromString(document.toString(), document.file.absolutePathString())
        val scanner = params.scanner.create(input, document).apply {
            scanner.errorListener = StitcherErrorListener(document)
        }
        val stream = FileTokenStream(document, CommonTokenStream(scanner))
        return FileAstParser.parse(stream).scope
    }
}