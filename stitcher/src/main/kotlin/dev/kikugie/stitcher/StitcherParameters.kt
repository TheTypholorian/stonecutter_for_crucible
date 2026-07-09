package dev.kikugie.stitcher

import dev.kikugie.stitcher.parser.ScannerAdapter
import dev.kikugie.stitcher.transformer.edit.*
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import dev.kikugie.stonecutter.model.process.ProcessParameters

public class StitcherParameters(
    public val vars: ProcessParameters,
    public val flags: ConfigurationRegistry,

    public val scanner: ScannerAdapter.Factory,
    public val commenter: CommentingStrategy,
    public val uncommenter: UncommentingStrategy,
    public val swapper: SwappingStrategy,
)