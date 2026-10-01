package dev.kikugie.stonecutter.controller.file

import dev.kikugie.stitcher.antlr.HashStyleScanner
import dev.kikugie.stitcher.antlr.JavaStyleScanner
import dev.kikugie.stitcher.antlr.KotlinStyleScanner
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
    useJavaScanner()

    commenter.set(JavaLikeCommentingStrategy.Java)
    uncommenter.set(SlashStarUncommentingStrategy)
}

private fun FileHandlerContainer.configureKotlin(): Unit = configure(
    "kt", "kts",
) {
    useKotlinScanner()

    commenter.set(JavaLikeCommentingStrategy.Kotlin)
    uncommenter.set(SlashStarUncommentingStrategy)
}

private fun FileHandlerContainer.configureShader(): Unit = configure(
    "fsh", "vsh", "glsl",
) {
    useJavaScanner()

    commenter.set(line("//"))
    uncommenter.set(SlashStarUncommentingStrategy)
}

private fun HandlerBuilder.useJavaScanner(): Unit = scanner {
    lexer.set(::JavaStyleScanner)
    openers(JavaStyleScanner.Tokens.SLASH_COMMENT_START, JavaStyleScanner.Tokens.STAR_COMMENT_START)
    closers(JavaStyleScanner.Tokens.SLASH_COMMENT_END, JavaStyleScanner.Tokens.STAR_COMMENT_END)
}

private fun HandlerBuilder.useKotlinScanner(): Unit = scanner {
    lexer.set(::KotlinStyleScanner)
    openers(KotlinStyleScanner.Tokens.SLASH_COMMENT_START, KotlinStyleScanner.Tokens.STAR_COMMENT_START)
    closers(KotlinStyleScanner.Tokens.SLASH_COMMENT_END, KotlinStyleScanner.Tokens.STAR_COMMENT_END)
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