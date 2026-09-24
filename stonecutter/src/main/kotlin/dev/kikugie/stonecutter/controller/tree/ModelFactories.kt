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
import dev.kikugie.stonecutter.model.process.ReplacementModel
import dev.kikugie.stonecutter.model.tree.*
import dev.kikugie.stonecutter.util.SCJSON
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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
    data.toParameters(), flags
)

internal fun ProjectTree.toModel(flags: ConfigurationRegistry): TreeModel =
    TreeModel(StonecutterPlugin.VERSION, vcs.project, current?.project, branches.map(ProjectBranch::toInfo), nodes.map(ProjectNode::toInfo), flags)

internal fun StonecutterBuildData.toParameters(): ProcessParameters = ProcessParameters(
    constants = constants.get().toMap(),
    targets = dependencies.get().mapValues { (_, it) -> LenientOperations.parse(it) },
    swaps = swaps.get().toMap(),
    replacements = replacements.get().toList(),
)

internal fun ProcessParameters.forFile(file: Path, handlers: Map<String, HandlerModel>, flags: ConfigurationRegistry): StitcherParameters? {
    val handler = handlers[file.extension.lowercase()] ?: return null
    return StitcherParameters(
        this, flags,
        handler.scanner, handler.commenter, handler.uncommenter, handler.swapper
    )
}

internal fun ConfigurationRegistry.toJsonString(): String {
    val encoded = SCJSON.encodeToJsonElement(ConfigurationRegistry.RegistrySerializer, this).jsonObject
    return JsonObject(encoded.filterValues { "value" in it.jsonObject }.toSortedMap()).toString()
}

internal fun restoreFromJson(snapshot: String): ConfigurationRegistry =
    SCJSON.decodeFromString(ConfigurationRegistry.RegistrySerializer, snapshot)

internal fun List<ReplacementModel>.resolveFiles(tree: FileTree): Map<Int, Set<File>> = buildMap {
    for ((index, it) in this@resolveFiles.withIndex()) if (it.isFiltered)
        this[index] = tree.matching(PatternSet().setIncludes(it.includes).setExcludes(it.excludes)).files
}

internal fun List<ReplacementModel>.unmatchedFiles(): Map<Int, Set<File>> = buildMap {
    for ((index, it) in this@unmatchedFiles.withIndex()) if (it.isFiltered) this[index] = emptySet()
}

internal fun ProcessParameters.restrictedTo(file: File, scopes: Map<Int, Set<File>>): ProcessParameters = if (scopes.isEmpty()) this
else copy(replacements = replacements.mapIndexed { index, it ->
    if (it.enabled && scopes[index]?.contains(file) == false) it.copy(enabled = false) else it
})

private val ReplacementModel.isFiltered: Boolean
    get() = includes.isNotEmpty() || excludes.isNotEmpty()
