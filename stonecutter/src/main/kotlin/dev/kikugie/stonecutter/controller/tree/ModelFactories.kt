package dev.kikugie.stonecutter.controller.tree

import dev.kikugie.stitcher.StitcherParameters
import dev.kikugie.stonecutter.StonecutterPlugin
import dev.kikugie.stonecutter.build.data.StonecutterBuildData
import dev.kikugie.stonecutter.controller.file.HandlerModel
import dev.kikugie.stonecutter.data.tree.ProjectBranch
import dev.kikugie.stonecutter.data.tree.ProjectNode
import dev.kikugie.stonecutter.data.tree.ProjectTree
import dev.kikugie.stonecutter.data.version.LenientOperations
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import dev.kikugie.stonecutter.model.process.ProcessParameters
import dev.kikugie.stonecutter.model.process.ReplacementFilter
import dev.kikugie.stonecutter.model.tree.*
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.util.PatternSet
import java.io.File
import java.nio.file.Path
import kotlin.io.path.extension

internal fun ProjectNode.toInfo(): NodeInfo =
    NodeInfo(metadata.project, metadata.version, metadata.isActive, location)

internal fun ProjectBranch.toInfo(): BranchInfo =
    BranchInfo(id, location)

internal fun ProjectBranch.toModel(): BranchModel =
    BranchModel(id, location, nodes.map(ProjectNode::toInfo))

internal fun ProjectNode.toModel(data: StonecutterBuildData, flags: ConfigurationRegistry): NodeModel = NodeModel(
    metadata.project, metadata.version, metadata.isActive, branch.toInfo(), tree.location,
    data.toParameters(), flags, data.replacementFilters.get().toMap()
)

internal fun ProjectTree.toModel(flags: ConfigurationRegistry): TreeModel =
    TreeModel(StonecutterPlugin.VERSION, vcs.project, current?.project, branches.map(ProjectBranch::toInfo), nodes.map(ProjectNode::toInfo), flags)

internal fun StonecutterBuildData.toParameters(): ProcessParameters = ProcessParameters(
    constants = constants.get().toMap(),
    targets = dependencies.get().mapValues { (_, it) -> LenientOperations.parse(it) },
    swaps = swaps.get().toMap(),
    replacements = replacements.get().toList(),
)

internal fun ProcessParameters.forFile(file: Path, handlers: Map<String, HandlerModel>): StitcherParameters? {
    val handler = handlers[file.extension.lowercase()] ?: return null
    return StitcherParameters(
        this, ConfigurationRegistry(),
        handler.scanner, handler.commenter, handler.uncommenter, handler.swapper
    )
}

internal fun Map<Int, ReplacementFilter>.resolveFiles(tree: FileTree): Map<Int, Set<File>> = mapValues { (_, it) ->
    tree.matching(PatternSet().setIncludes(it.includes).setExcludes(it.excludes)).files
}

internal fun ProcessParameters.restrictedTo(file: File, scopes: Map<Int, Set<File>>): ProcessParameters {
    if (scopes.isEmpty()) return this
    val allowed = replacements.filterIndexed { index, _ -> scopes[index]?.contains(file) ?: true }
    return if (allowed.size == replacements.size) this else copy(replacements = allowed)
}
