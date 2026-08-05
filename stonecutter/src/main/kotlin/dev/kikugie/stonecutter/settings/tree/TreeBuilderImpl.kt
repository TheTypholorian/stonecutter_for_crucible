package dev.kikugie.stonecutter.settings.tree

import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.ProjectReference
import dev.kikugie.stonecutter.controller.StonecutterControllerManager
import dev.kikugie.stonecutter.data.ProjectHierarchy
import dev.kikugie.stonecutter.data.ProjectHierarchy.Companion.hierarchy
import dev.kikugie.stonecutter.data.StonecutterProject
import dev.kikugie.stonecutter.data.deserialization.SCConverter
import dev.kikugie.stonecutter.settings.StonecutterSettingsExtension
import dev.kikugie.stonecutter.settings.StonecutterSettingsImpl
import dev.kikugie.stonecutter.util.isValidIdentifier
import dev.kikugie.stonecutter.util.toRegularFile
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.initialization.Settings
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.newInstance
import java.io.File
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.notExists

private const val ROOT: String = ""

internal abstract class TreeBuilderImpl @Inject constructor(val objects: ObjectFactory, val providers: ProviderFactory, hierarchy: String)
    : TreeBuilder(), Named {
    private val name: String = "TreeBuilderImpl@$hierarchy"
    internal var localBuildScriptProvider: ((Identifier, StonecutterProject) -> String)? = null
        private set
    internal val branchBuilders: MutableMap<Identifier, BranchBuilderImpl> = mutableMapOf()
    private lateinit var resolvedVcsVersion: StonecutterProject
    private lateinit var resolvedController: StonecutterControllerManager

    internal val vcsProject: StonecutterProject
        get() = resolvedVcsVersion
    internal val controller: StonecutterControllerManager
        get() = resolvedController

    init {
        @Suppress("DEPRECATION") branchScript.disallowChanges()
    }

    override fun inherit(branches: Iterable<Identifier>) {
        require(branches.any()) { "Root branch has no parent to inherit from" }
        getOrCreateBranch(ROOT).inherit(branches)
    }

    override fun branch(name: Identifier, config: Action<BranchBuilder>) {
        require(name.isEmpty() || name.isValidIdentifier()) { "Invalid branch name: '$name'" }
        getOrCreateBranch(name).let(config::execute)
    }

    override fun versions(entries: List<StonecutterProject>): NodeBuilder =
        getOrCreateBranch(ROOT).versions(entries)

    override fun mapBuilds(mapping: (branch: Identifier, node: StonecutterProject) -> String) {
        localBuildScriptProvider = mapping
    }

    override fun loadImpl(file: File, converter: SCConverter) {
        val contents: String = providers.fileContents(file.toRegularFile(objects)).asText.orNull
            ?: throw NoSuchFileException(file)
        loadImpl(contents, converter)
    }

    override fun loadImpl(data: String, converter: SCConverter) =
        deserializeTreeTo(this, data, converter)

    override fun getName(): String = name

    internal fun build(ext: StonecutterSettingsImpl, ref: ProjectReference) {
        val project = ext.settings.rootProject.resolve(ref, ext.settings)
        prepare(ext, project)
        create(ext, project)
        ext.treeBuilderContainer += this
    }

    internal fun prepare(ext: StonecutterSettingsImpl, project: ProjectDescriptor) {
        for (builder in branchBuilders.values) builder.prepare(ext, project)
        checkVersionIntegrity()
        resolvedVcsVersion = resolveVcs(project.hierarchy)
        resolvedController = resolveController(ext, project)
    }

    internal fun create(ext: StonecutterSettingsImpl, project: ProjectDescriptor) {
        with(project.projectDir.resolve(resolvedController.filename).toPath()) {
            project.buildFileName = fileName.toString()
            if (notExists()) resolvedController.create(this, resolvedVcsVersion.project)
        }

        for (builder in branchBuilders.values) builder.create(ext, project)
    }

    private fun getOrCreateBranch(name: String): BranchBuilderImpl = branchBuilders.getOrPut(name) {
        objects.newInstance<BranchBuilderImpl>(name, this)
    }

    private fun resolveVcs(project: ProjectHierarchy): StonecutterProject = when(val vcs = vcsVersion.orNull) {
        null -> checkNotNull(branchBuilders.values.firstOrNull()?.versions?.firstOrNull()?.data) {
            "No versions have been registered for project '$project'"
        }
        else -> {
            val valid = branchBuilders.values.firstNotNullOfOrNull { br ->
                br.versions.firstOrNull { v -> v.project == vcs }?.data
            }
            checkNotNull(valid) { "VCS version '$vcs' doesn't match any registered subproject for project '$project'" }
        }
    }

    private fun resolveController(ext: StonecutterSettingsImpl, project: ProjectDescriptor): StonecutterControllerManager {
        val isKotlin = kotlinController.orNull
            ?: ext.kotlinController.orNull
            ?: getDefaultBuildscript(ext, project.projectDir.toPath(), "stonecutter").endsWith("kts")
        return if (isKotlin) StonecutterControllerManager.Kotlin
        else StonecutterControllerManager.Groovy
    }

    private fun checkVersionIntegrity() {
        val lookup = mutableMapOf<String, BuiltNodeInfo>()
        for (builder in branchBuilders.values) for (node in builder.versions) {
            val registered = lookup.getOrPut(node.project) { node }.version
            check(registered == node.version) { "Project '${node.hierarchy}' can't be registered as both $registered and ${node.version}" }
        }
    }
}

internal abstract class BranchBuilderImpl @Inject constructor(
    val name: String,
    val tree: TreeBuilderImpl,
    val objects: ObjectFactory
) : BranchBuilder() {
    private val nodeBuilders: MutableList<NodeBuilderImpl> = mutableListOf()
    private val inheritedBranches: MutableSet<Identifier> = mutableSetOf()
    private var inheritAll: Boolean = false
    private lateinit var builtNodes: Map<String, BuiltNodeInfo>
    private lateinit var buildFile: String

    internal val versions: Collection<BuiltNodeInfo>
        get() = builtNodes.values

    override fun inherit(branches: Iterable<Identifier>) {
        check(!::builtNodes.isInitialized) { "Branch '$name' has already been built" }
        val collected = branches.toSet()
        if (collected.size == 1 && collected.first() == "*") inheritAll = true else {
            require(name !in collected) { "Branch '$name' can't inherit from itself" }
            inheritedBranches += collected
        }
    }

    override fun versions(entries: List<StonecutterProject>): NodeBuilder {
        check(!::builtNodes.isInitialized) { "Branch '$name' has already been built" }
        return objects.newInstance<NodeBuilderImpl>(entries, this).also(nodeBuilders::add)
    }

    internal fun prepare(ext: StonecutterSettingsImpl, project: ProjectDescriptor) {
        builtNodes = prepareNodes(ext, project)
        if (name.isNotEmpty()) {
            val filename = resolveBuild(ext, project)
            checkBuildscript(project.hierarchy.resolve(name), filename)
            buildFile = filename
        }
    }

    internal fun create(ext: StonecutterSettingsImpl, project: ProjectDescriptor) {
        val project = project.resolve(name, ext.settings)
            .apply { projectDir.toPath().createDirectories() }
        if (::buildFile.isInitialized) project.buildFileName = buildFile
        for (node in builtNodes.values) node.create(ext, project)
    }

    private fun prepareNodes(ext: StonecutterSettingsImpl, project: ProjectDescriptor): Map<String, BuiltNodeInfo> = buildMap {
        val branches = if (inheritAll) tree.branchBuilders.values
        else inheritedBranches.map { checkNotNull(tree.branchBuilders[it]) { "Can't inherit from non-existent branch '$it'" } }

        for (branch in branches) prepareNodes(this, branch.nodeBuilders, ext, project)
        prepareNodes(this, nodeBuilders, ext, project)
    }

    private fun prepareNodes(to: MutableMap<String, BuiltNodeInfo>, nodes: List<NodeBuilderImpl>, ext: StonecutterSettingsImpl, project: ProjectDescriptor) {
        for (node in nodes) node.prepare(to, ext, project)
    }

    private fun resolveBuild(ext: StonecutterSettingsImpl, project: ProjectDescriptor): String = branchScript.orNull
        ?: getDefaultBuildscript(ext, project.projectDir.toPath(), "branch")
}

internal abstract class NodeBuilderImpl @Inject constructor(
    val versions: List<StonecutterProject>,
    val branch: BranchBuilderImpl
) : NodeBuilder() {
    val tree: TreeBuilderImpl inline get() = branch.tree

    internal fun prepare(to: MutableMap<String, BuiltNodeInfo>, ext: StonecutterSettingsImpl, project: ProjectDescriptor) {
        for (data in versions) {
            val file = data.resolveBuild(ext, project)
            val hierarchy = project.hierarchy.resolve(data.project)
            checkBuildscript(hierarchy, file)
            to[data.project] = BuiltNodeInfo(data, hierarchy, file)
        }
    }

    private fun StonecutterProject.resolveBuild(ext: StonecutterSettingsImpl, project: ProjectDescriptor): String = buildscript.orNull
        ?: tree.localBuildScriptProvider?.invoke(branch.name, this)
        ?: tree.centralScript.orNull
        ?: ext.centralScript.orNull
        ?: getDefaultBuildscript(ext, project.projectDir.toPath(), "build")
}

internal class BuiltNodeInfo(val data: StonecutterProject, val hierarchy: ProjectHierarchy, val buildscript: String) {
    val project: String inline get() = data.project
    val version: String inline get() = data.version

    internal fun create(ext: StonecutterSettingsImpl, parent: ProjectDescriptor) {
        val project = parent.resolve(data.project, ext.settings)
        project.projectDir = parent.projectDir.resolve("versions/${data.project}")
            .apply { toPath().createDirectories() }
        project.buildFileName = "../../$buildscript"
    }
}

private fun checkBuildscript(project: ProjectHierarchy, build: String) {
    check(build.isNotBlank()) { "Buildscript name can't be blank for '$project'" }
    check(!build.startsWith("stonecutter.gradle")) { "Buildscript name can't be `stonecutter.gradle(.kts)` for '$project'" }
}

private fun getDefaultBuildscript(ext: StonecutterSettingsImpl, dir: Path, type: String): String = when {
    dir.resolve("$type.gradle.kts").exists() -> "$type.gradle.kts"
    dir.resolve("$type.gradle").exists() || ext.isHardMode -> "$type.gradle"
    else -> "$type.gradle.kts"
}

private fun register(path: String, settings: Settings): ProjectDescriptor = with(path.trim(':')) {
    if (isNotEmpty()) settings.include(this)
    return settings.project(":$this")
}

private tailrec fun ProjectDescriptor.resolve(relative: ProjectReference, settings: Settings): ProjectDescriptor = when(relative) {
    is CharSequence -> if (relative.isEmpty()) this else register("$path:$relative", settings)
    is ProjectDescriptor -> register(path, settings)
    is Provider<*> -> resolve(relative.get(), settings)
    else -> throw IllegalArgumentException("Unsupported project type ${relative::class.qualifiedName}")
}
