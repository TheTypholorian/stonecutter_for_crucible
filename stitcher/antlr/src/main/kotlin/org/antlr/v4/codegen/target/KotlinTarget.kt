package org.antlr.v4.codegen.target

import dev.kikugie.antlr.util.toCamelCase
import org.antlr.v4.codegen.CodeGenerator
import org.stringtemplate.v4.STGroup
import org.stringtemplate.v4.StringRenderer
import java.util.Locale

public class KotlinTarget(gen: CodeGenerator) : JavaTarget(gen) {
    override fun getVersion(): String = ANTLR_VERSION
    override fun getReservedWords(): Set<String> = RESERVED_KEYWORDS
    override fun getTargetCharValueEscape(): Map<Char, String> = CHAR_ESCAPES

    override fun loadTemplates(): STGroup? = super.loadTemplates().apply {
        registerRenderer(String::class.java, StringRendererEx())
    }

    override fun getTargetStringLiteralFromANTLRStringLiteral(
        generator: CodeGenerator, literal: String, addQuotes: Boolean, escapeSpecial: Boolean
    ): String = super
        .getTargetStringLiteralFromANTLRStringLiteral(generator, literal, addQuotes, escapeSpecial)
        .replace("$", "\\$")

    private class StringRendererEx : StringRenderer() {
        override fun toString(value: String?, formatString: String?, locale: Locale?): String? = when (formatString) {
            "camel" -> value?.toCamelCase(false, locale ?: Locale.getDefault(), '_', '-', ' ', '.')
            "pascal" -> value?.toCamelCase(true, locale ?: Locale.getDefault(), '_', '-', ' ', '.')
            else -> super.toString(value, formatString, locale)
        }
    }

    private companion object {
        const val ANTLR_VERSION = "4.13.2"

        val RESERVED_KEYWORDS: Set<String> = setOf(
            // https://kotlinlang.org/docs/keyword-reference.html#hard-keywords
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
            "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
            "true", "try", "typealias", "typeof", "val", "var", "when", "while",

            // ANTLR-default
            "rule", "parserRule", "reset",
        )

        val CHAR_ESCAPES: Map<Char, String> = buildMap {
            this += defaultCharValueEscape
            this['$'] = "\\$"
            this[11.toChar()] = "\\u000b"
            this[12.toChar()] = "\\u000c"
            this[14.toChar()] = "\\u000e"
            this[15.toChar()] = "\\u000f"
        }
    }
}