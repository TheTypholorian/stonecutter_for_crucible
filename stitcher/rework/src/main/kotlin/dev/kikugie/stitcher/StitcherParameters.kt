package dev.kikugie.stitcher

import dev.kikugie.stitcher.parser.ScannerAdapter
import dev.kikugie.stitcher.transformer.edit.UncommentingStrategy
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import dev.kikugie.stonecutter.model.process.ProcessParameters

class StitcherParameters(
    val vars: ProcessParameters,
    val flags: ConfigurationRegistry,

    val scanner: ScannerAdapter.Factory,
    val uncommenter: UncommentingStrategy,
)
