package dev.kikugie.stitcher.replacement

import dev.kikugie.stonecutter.model.process.Replacement

class ReplacementState<out T : Replacement>(val value: T, var enabled: Boolean)
