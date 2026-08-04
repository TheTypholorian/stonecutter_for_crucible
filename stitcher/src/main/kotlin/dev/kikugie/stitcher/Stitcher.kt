package dev.kikugie.stitcher

import dev.kikugie.commons.takeAs
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.issue.runMultiThrowing
import dev.kikugie.stitcher.token.BlockToken
import dev.kikugie.stitcher.transformer.ExpressionChecker
import dev.kikugie.stitcher.transformer.FileAstAssembler
import dev.kikugie.stitcher.transformer.FileAstUncommenter
import dev.kikugie.stitcher.util.parse

object Stitcher {
    @JvmStatic fun process(document: Document, parameters: StitcherParameters): BlockToken.Root = document.runMultiThrowing { marker ->
        val original: BlockToken.Root = parse(document, parameters.scanner)
        val uncommented: BlockToken.Root = original.accept(FileAstUncommenter(parameters), null).takeAs<BlockToken.Root>()
            .apply { accept(ExpressionChecker, ExpressionChecker.Context(parameters.vars)) }
        marker.check()

        uncommented.accept(FileAstAssembler(parameters), null).takeAs()
    }
}