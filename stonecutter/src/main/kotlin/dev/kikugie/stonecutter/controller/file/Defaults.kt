package dev.kikugie.stonecutter.controller.file

import dev.kikugie.stitcher.antlr.HashStyleScanner
import dev.kikugie.stitcher.antlr.SlashStyleScanner
import dev.kikugie.stitcher.antlr.XmlStyleScanner
import dev.kikugie.stitcher.transformer.edit.impl.JavaLikeCommentingStrategy
import dev.kikugie.stitcher.transformer.edit.impl.SlashStarUncommentingStrategy
import dev.kikugie.stitcher.transformer.edit.impl.XmlCommentingStrategy
import dev.kikugie.stitcher.transformer.edit.impl.XmlUncommentingStrategy

internal fun FileHandlerContainer.configureDefaults() {
    configureJava()
    configureKotlin()
    configureShader()
    configureHash()
    configureXml()
    configurePlainText()
}

private fun FileHandlerContainer.configureJava(): Unit = configure(
    "java", "scala", "sc", "groovy", "gradle", "json5",
) {
    useSlashScanner(nested = false)

    commenter.set(JavaLikeCommentingStrategy.Java)
    uncommenter.set(SlashStarUncommentingStrategy)
}

private fun FileHandlerContainer.configureKotlin(): Unit = configure(
    "kt", "kts",
) {
    useSlashScanner(nested = true)

    commenter.set(JavaLikeCommentingStrategy.Kotlin)
    uncommenter.set(SlashStarUncommentingStrategy)
}

private fun FileHandlerContainer.configureShader(): Unit = configure(
    "fsh", "vsh", "glsl",
) {
    useSlashScanner(nested = false)

    commenter.set(line("//"))
    uncommenter.set(SlashStarUncommentingStrategy)
}

private fun HandlerBuilder.useSlashScanner(nested: Boolean): Unit = scanner {
    if (!nested) lexer.set(::SlashStyleScanner)
    else lexer.set { SlashStyleScanner(it).apply { nestMultiLineComments = true } }

    openers(SlashStyleScanner.Tokens.SLASH_COMMENT_START, SlashStyleScanner.Tokens.STAR_COMMENT_START)
    closers(SlashStyleScanner.Tokens.SLASH_COMMENT_END, SlashStyleScanner.Tokens.STAR_COMMENT_END)
}

private fun FileHandlerContainer.configureHash(): Unit = configure(
    "cfg", "aw", "accesswidener", "ct", "classtweaker", "yml", "yaml", "toml", "properties",
) {
    scanner {
        lexer.set(::HashStyleScanner)
        openers(HashStyleScanner.Tokens.HASH_COMMENT_START)
        closers(HashStyleScanner.Tokens.HASH_COMMENT_END)
    }

    commenter.set(line("#"))
}

private fun FileHandlerContainer.configureXml(): Unit = configure(
    "xml", "html", "htm", "xhtml", "svg", "xsd", "xsl"
) {
    scanner {
        lexer.set(::XmlStyleScanner)
        openers(XmlStyleScanner.Tokens.XML_COMMENT_START)
        closers(XmlStyleScanner.Tokens.XML_COMMENT_END)
    }

    commenter.set(XmlCommentingStrategy)
    uncommenter.set(XmlUncommentingStrategy)
}

private fun FileHandlerContainer.configurePlainText(): Unit = configure(
    "json", "mcmeta", "txt", "md", "csv",
) {
}