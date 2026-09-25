package dev.kikugie.stonecutter.build.config

import dev.kikugie.stonecutter.model.process.FilesModel

internal class StonecutterFilesImpl(val processable: PatternSetFilter = PatternSetFilter()) : StonecutterFiles, PatternFilter by processable {
    private val disabled: MutableSet<String> = LinkedHashSet()
    private val enabled: MutableSet<String> = LinkedHashSet()

    override fun disable(direction: Boolean, patterns: Iterable<String>) {
        if (direction) disabled += patterns else enabled += patterns
    }

    fun toModel(): FilesModel = FilesModel(includes.toSet(), excludes.toSet(), disabled.toSet(), enabled.toSet())
}
