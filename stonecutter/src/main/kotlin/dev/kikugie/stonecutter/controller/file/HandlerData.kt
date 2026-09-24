package dev.kikugie.stonecutter.controller.file

import dev.kikugie.stitcher.transformer.edit.CommentingStrategy
import dev.kikugie.stitcher.transformer.edit.SwappingStrategy
import dev.kikugie.stitcher.transformer.edit.UncommentingStrategy
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/** Gradle configuration cache compatible container. */
internal interface HandlerData {
    val openers: SetProperty<Int>
    val closers: SetProperty<Int>
    val lexer: Property<ScannerBuilder.LexerFactory>
    val commenter: Property<CommentingStrategy>
    val uncommenter: Property<UncommentingStrategy>
    val swapper: Property<SwappingStrategy>
}
