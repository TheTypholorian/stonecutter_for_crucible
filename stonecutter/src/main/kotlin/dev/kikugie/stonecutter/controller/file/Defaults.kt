package dev.kikugie.stonecutter.controller.file

import dev.kikugie.stitcher.antlr.scanner.HashStyleScanner
import dev.kikugie.stitcher.antlr.scanner.SlashStyleScanner
import dev.kikugie.stitcher.transformer.impl.LineCommentStrategy
import dev.kikugie.stitcher.transformer.impl.SlashStarCommentStrategy
import dev.kikugie.stitcher.transformer.impl.StarRemoveCommentStrategy

internal fun FileHandlerContainer.configureDefaults() {
    configureJava()
    configureKotlin()
    configureHash()
}

private fun FileHandlerContainer.configureJava() = configure("java", "scala", "sc", "groovy", "gradle", "fsh", "vsh", "json5") {
    scanner {
        lexer.set(::SlashStyleScanner)
        openers(SlashStyleScanner.SLASH_COMMENT_START, SlashStyleScanner.STAR_COMMENT_START)
        closers(SlashStyleScanner.SLASH_COMMENT_END, SlashStyleScanner.STAR_COMMENT_END)
    }

    commenter.set(SlashStarCommentStrategy(true))
    uncommenter.set(StarRemoveCommentStrategy)
}

private fun FileHandlerContainer.configureKotlin() = configure("kt", "kts") {
    scanner {
        lexer.set { SlashStyleScanner(it).apply { nestMultiLineComments = true } }
        openers(SlashStyleScanner.SLASH_COMMENT_START, SlashStyleScanner.STAR_COMMENT_START)
        closers(SlashStyleScanner.SLASH_COMMENT_END, SlashStyleScanner.STAR_COMMENT_END)
    }

    commenter.set(SlashStarCommentStrategy(false))
    uncommenter.set(StarRemoveCommentStrategy)
}

private fun FileHandlerContainer.configureHash() = configure("cfg", "aw", "accesswidener", "ct", "classtweaker", "yml", "yaml") {
    scanner {
        lexer.set(::HashStyleScanner)
        openers(HashStyleScanner.HASH_COMMENT_START)
        closers(HashStyleScanner.HASH_COMMENT_END)
    }

    commenter.set(LineCommentStrategy("#"))
}