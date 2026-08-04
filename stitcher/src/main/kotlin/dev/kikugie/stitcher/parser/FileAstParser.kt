package dev.kikugie.stitcher.parser

import dev.kikugie.stitcher.token.BlockToken

object FileAstParser {
    fun parse(stream: FileTokenStream): BlockToken.Root {
        val root = RootBuilder(stream.document).apply { accept(stream) }
        root.reportProblems()
        return root.build()
    }
}
