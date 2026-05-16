package dev.kikugie.stonecutter.settings.tree

import dev.kikugie.stonecutter.AnyVersion
import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.data.deserialization.SCConverter
import dev.kikugie.stonecutter.data.deserialization.deserialize
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.collections.iterator
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

internal fun deserializeTreeTo(builder: TreeBuilder, data: String, converter: SCConverter): Unit =
    converter.deserialize<SerializedTree>(data).applyTo(builder)

@Serializable
private sealed interface SerializedTree {
    val vcs: Identifier?
    val kotlinController: Boolean?

    fun applyTo(builder: TreeBuilder) {
        vcs?.let(builder.vcsVersion::set)
        kotlinController?.let(builder.kotlinController::set)
    }

    @Serializable
    class Default(
        override val vcs: Identifier? = null,
        @SerialName("kotlin_controller")
        override val kotlinController: Boolean? = null,
        val versions: TreeLayout.VersionsOnly = TreeLayout.VersionsOnly(),
        val branches: TreeLayout.BranchesFirst = TreeLayout.BranchesFirst(),
    ) : SerializedTree {
        override fun applyTo(builder: TreeBuilder) {
            super.applyTo(builder)
            versions.applyTo(builder)
            branches.applyTo(builder)
        }
    }

    @Serializable
    class Inverted(
        override val vcs: Identifier? = null,
        @SerialName("kotlin_controller")
        override val kotlinController: Boolean? = null,
        val versions: TreeLayout.VersionsFirst = TreeLayout.VersionsFirst(),
        val branches: TreeLayout.BranchesFirst = TreeLayout.BranchesFirst(),
    ) : SerializedTree {
        override fun applyTo(builder: TreeBuilder) {
            super.applyTo(builder)
            versions.applyTo(builder)
            branches.applyTo(builder)
        }
    }

    object Serializer : JsonContentPolymorphicSerializer<SerializedTree>(SerializedTree::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<SerializedTree> = when (element) {
            is JsonObject -> selectDeserializerFor(element)
            else -> throw SerializationException("Can't deserialize SerializedTree from ${element::class.simpleName}")
        }

        private fun selectDeserializerFor(obj: JsonObject): DeserializationStrategy<SerializedTree> = when {
            obj["versions"] is JsonObject -> Inverted.serializer()
            else -> Default.serializer()
        }
    }
}

private sealed interface TreeLayout {
    fun applyTo(builder: TreeBuilder)

    @Serializable @JvmInline
    value class VersionsOnly(val list: List<SerializedVersion> = emptyList()) : TreeLayout {
        override fun applyTo(builder: TreeBuilder) = builder.branch("") {
            for (node in list) node.applyTo(this)
        }
    }

    @Serializable @JvmInline
    value class BranchesFirst(val map: Map<String, BranchLayout> = emptyMap()) : TreeLayout {
        override fun applyTo(builder: TreeBuilder) {
            for ((name, layout) in map)
                builder.branch(name) { layout.applyTo(this) }
        }
    }

    @Serializable @JvmInline
    value class VersionsFirst(val map: Map<SerializedVersion.Primitive, VersionLayout> = emptyMap()) : TreeLayout {
        override fun applyTo(builder: TreeBuilder) {
            for ((version, layout) in map)
                layout.applyTo(version, builder)
        }
    }
}

@Serializable
private sealed interface BranchLayout {
    val inherit: InheritLayout
    val versions: List<SerializedVersion>

    fun applyTo(builder: BranchBuilder) {
        inherit.applyTo(builder)
        for (node in versions) node.applyTo(builder)
    }

    @Serializable @JvmInline
    value class VersionsOnly(override val versions: List<SerializedVersion>) : BranchLayout {
        override val inherit: InheritLayout get() = InheritLayout.Multiple()
    }

    @Serializable
    class Complete(
        override val inherit: InheritLayout = InheritLayout.Multiple(),
        override val versions: List<SerializedVersion> = emptyList(),
    ) : BranchLayout

    object Serializer : JsonContentPolymorphicSerializer<BranchLayout>(BranchLayout::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<BranchLayout> = when (element) {
            is JsonArray -> VersionsOnly.serializer()
            is JsonObject -> Complete.serializer()
            else -> throw SerializationException("Can't deserialize BranchLayout from ${element::class.simpleName}")
        }
    }
}

@Serializable
private sealed interface VersionLayout {
    val branches: List<Identifier>

    fun applyTo(version: SerializedVersion, builder: TreeBuilder) {
        for (id in branches) builder.branch(id) { version.applyTo(this) }
    }

    @Serializable @JvmInline
    value class Single(private val value: String) : VersionLayout {
        override val branches: List<Identifier> get() = listOf(value)
    }

    @Serializable @JvmInline
    value class BranchesOnly(override val branches: List<Identifier>) : VersionLayout

    @Serializable
    class Complete(override val branches: List<Identifier>) : VersionLayout

    object Serializer : JsonContentPolymorphicSerializer<VersionLayout>(VersionLayout::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<VersionLayout> = when(element) {
            is JsonPrimitive if (element.isString) -> Single.serializer()
            is JsonArray -> BranchesOnly.serializer()
            is JsonObject -> Complete.serializer()
            else -> throw SerializationException("Can't deserialize VersionLayout from ${element::class.simpleName}")
        }
    }
}

@Serializable
private sealed interface InheritLayout {
    fun applyTo(builder: BranchBuilder)

    @Serializable @JvmInline
    value class Single(private val value: String) : InheritLayout {
        override fun applyTo(builder: BranchBuilder): Unit = builder.inherit(value)
    }

    @Serializable @JvmInline
    value class Multiple(private val values: Set<String> = emptySet()) : InheritLayout {
        init {
            require(values.size <= 1 || "*" !in values) { "Multi-inheritance can't contain a '*' wildcard" }
        }

        override fun applyTo(builder: BranchBuilder) = builder.inherit(values)
    }

    object Serializer : JsonContentPolymorphicSerializer<InheritLayout>(InheritLayout::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<InheritLayout> = when (element) {
            is JsonPrimitive if (element.isString) -> Single.serializer()
            is JsonArray -> Multiple.serializer()
            else -> throw SerializationException("Can't deserialize InheritLayout from ${element::class.simpleName}")
        }
    }
}

@Serializable(with = SerializedVersion.Serializer::class)
private sealed interface SerializedVersion {
    val project: Identifier
    val version: AnyVersion
    val buildscript: String?

    fun applyTo(builder: BranchBuilder) {
        val node = builder.version(project, version)
        buildscript?.let(node::buildscript)
    }

    @JvmInline @Serializable
    value class Primitive(private val value: String) : SerializedVersion {
        override val project: Identifier get() = value.substringBefore(':')
        override val version: AnyVersion get() = value.substringAfter(':', "").substringBefore(':').ifEmpty { project }
        override val buildscript: String? get() = if (value.count { it == ':' } > 1) value.substringAfterLast(':') else null
    }

    @Serializable
    class Composite(
        override val project: Identifier,
        override val version: AnyVersion = project,
        override val buildscript: String? = null,
    ) : SerializedVersion

    object Serializer : JsonContentPolymorphicSerializer<SerializedVersion>(SerializedVersion::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<SerializedVersion> = when(element) {
            is JsonPrimitive -> Primitive.serializer()
            is JsonObject -> Composite.serializer()
            else -> error("Can't deserialize SerializedVersion from ${element::class.simpleName}")
        }
    }
}
