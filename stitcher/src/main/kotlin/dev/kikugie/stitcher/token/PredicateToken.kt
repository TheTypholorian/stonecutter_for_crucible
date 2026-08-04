package dev.kikugie.stitcher.token

import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.semver.data.StringVersion
import dev.kikugie.semver.data.Version
import dev.kikugie.semver.data.VersionOperator
import dev.kikugie.stitcher.antlr.StitcherLexer.Tokens
import dev.kikugie.stitcher.token.type.BuiltinTokenType

data class PredicateToken(val comp: LeafToken?, val target: LeafToken) : StitcherToken {
    override val type: BuiltinTokenType get() = BuiltinTokenType.PREDICATE

    val operator: VersionOperator by lazy { matchVersionOperator(comp) }
    val version: Version by lazy { parseVersionString(target) }
}

private fun matchVersionOperator(token: LeafToken?): VersionOperator = when (token.typeValue) {
    Tokens.COMP_EQUAL -> VersionOperator.EQUAL
    Tokens.COMP_NOT_EQUAL -> VersionOperator.NOT_EQUAL
    Tokens.COMP_MORE -> VersionOperator.GREATER
    Tokens.COMP_GR_MORE -> VersionOperator.GREATER_EQUAL
    Tokens.COMP_LESS -> VersionOperator.LESS
    Tokens.COMP_GR_LESS -> VersionOperator.LESS_EQUAL
    Tokens.REPL_MARK -> VersionOperator.SAME_MINOR
    Tokens.COMP_MAJOR -> VersionOperator.SAME_MAJOR
    else -> VersionOperator.IMPLICIT_EQUAL
}

private fun parseVersionString(token: LeafToken): Version {
    // [typeValue] is guaranteed to be either [Tokens.IDENTIFIER] or [Tokens.VERSION] by the parser
    if (token.typeValue != Tokens.VERSION)
        return StringVersion(token.text)

    // The parser guarantees the token text to be valid semver, so simple splits are more efficient than [SemanticVersion.parse]
    val preReleaseSplit = token.text.indexOf('-')
    val buildMetadataSplit = token.text.indexOf('+')

    val versionCore = when {
        preReleaseSplit > 0 -> token.text.take(preReleaseSplit)
        buildMetadataSplit > 0 -> token.text.take(buildMetadataSplit)
        else -> token.text
    }.split('.').map(String::toInt)

    val preRelease = when {
        preReleaseSplit < 0 -> ""
        buildMetadataSplit < 0 -> token.text.substring(preReleaseSplit + 1)
        else -> token.text.substring(preReleaseSplit + 1, buildMetadataSplit)
    }

    val buildMetadata = when {
        buildMetadataSplit < 0 -> ""
        else -> token.text.substring(buildMetadataSplit + 1)
    }

    return SemanticVersion(versionCore, preRelease, buildMetadata)
}
