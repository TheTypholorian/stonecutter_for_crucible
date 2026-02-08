package dev.kikugie.stitcher.transformer

import dev.kikugie.semver.data.Version
import dev.kikugie.stitcher.parser.adapter.ScannerAdapter
import dev.kikugie.stitcher.replacement.Replacement
import dev.kikugie.stitcher.transformer.strategy.CommentingStrategy
import dev.kikugie.stitcher.transformer.strategy.SwappingStrategy
import dev.kikugie.stitcher.transformer.strategy.UncommentingStrategy

class TransformParameters(
    val adapter: ScannerAdapter.Factory,
    val commenter: CommentingStrategy,
    val uncommenter: UncommentingStrategy,
    val swapper: SwappingStrategy,

    val swaps: Map<String, String>,
    val constants: Map<String, Boolean>,
    val dependencies: Map<String, Version>,
    val replacements: List<Replacement>
) : ScannerAdapter.Factory by adapter, CommentingStrategy by commenter, UncommentingStrategy by uncommenter, SwappingStrategy by swapper