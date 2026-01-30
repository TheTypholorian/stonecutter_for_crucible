package dev.kikugie.stitcher.transformer

import dev.kikugie.semver.data.Version
import dev.kikugie.stitcher.parser.adapter.ScannerAdapter
import dev.kikugie.stitcher.replacement.Replacement
import dev.kikugie.stitcher.transformer.strategy.CommentingStrategy
import dev.kikugie.stitcher.transformer.strategy.SwappingStrategy
import dev.kikugie.stitcher.transformer.strategy.UncommentingStrategy

data class TransformParameters(
    val adapter: ScannerAdapter.Factory,

    val commenter: CommentingStrategy,
    val uncommenter: UncommentingStrategy,
    val replacer: SwappingStrategy,

    val swaps: Map<String, String> = emptyMap(),
    val constants: Map<String, Boolean> = emptyMap(),
    val dependencies: Map<String, Version> = emptyMap(),

    val replacements: List<Replacement> = emptyList()
)