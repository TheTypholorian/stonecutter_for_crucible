package dev.kikugie.stonecutter.controller.file

import dev.kikugie.stitcher.antlr.HashStyleScanner
import dev.kikugie.stitcher.antlr.SlashStyleScanner
import dev.kikugie.stitcher.transformer.edit.impl.SlashStarUncommentingStrategy
import dev.kikugie.stitcher.transformer.edit.impl.StarCommentingStrategy

internal fun FileHandlerContainer.configureDefaults() {
    configureJava()
    configureKotlin()
    configureShader()
    configureHash()
}

private fun FileHandlerContainer.configureJava() = configure("java", "scala", "sc", "groovy", "gradle", "json5") {
    useSlashScanner(nested = false)

    commenter.set(StarCommentingStrategy.Java)
    uncommenter.set(SlashStarUncommentingStrategy)
}

private fun FileHandlerContainer.configureKotlin() = configure("kt", "kts") {
    useSlashScanner(nested = true)

    commenter.set(StarCommentingStrategy.Kotlin)
    uncommenter.set(SlashStarUncommentingStrategy)
}

private fun FileHandlerContainer.configureShader() = configure("fsh", "vsh") {
    useSlashScanner(nested = false)

    commenter.set(line("//"))
    uncommenter.set(SlashStarUncommentingStrategy)
}

private fun HandlerBuilder.useSlashScanner(nested: Boolean) = scanner {
    if (!nested) lexer.set(::SlashStyleScanner)
    else lexer.set { SlashStyleScanner(it).apply { nestMultiLineComments = true } }

    openers(SlashStyleScanner.Tokens.SLASH_COMMENT_START, SlashStyleScanner.Tokens.STAR_COMMENT_START)
    closers(SlashStyleScanner.Tokens.SLASH_COMMENT_END, SlashStyleScanner.Tokens.STAR_COMMENT_END)
}

private fun FileHandlerContainer.configureHash() = configure("cfg", "aw", "accesswidener", "ct", "classtweaker", "yml", "yaml") {
    scanner {
        lexer.set(::HashStyleScanner)
        openers(HashStyleScanner.Tokens.HASH_COMMENT_START)
        closers(HashStyleScanner.Tokens.HASH_COMMENT_END)
    }

    commenter.set(line("#"))
}