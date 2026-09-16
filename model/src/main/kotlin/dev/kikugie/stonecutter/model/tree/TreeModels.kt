@file:UseSerializers(PathSerializer::class)

package dev.kikugie.stonecutter.model.tree

import dev.kikugie.stonecutter.model.AnyVersion
import dev.kikugie.stonecutter.model.Identifier
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import dev.kikugie.stonecutter.model.process.ProcessParameters
import dev.kikugie.stonecutter.model.process.ReplacementFilter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString

@Serializable @JvmRecord
public data class NodeInfo(
    val project: Identifier,
    val version: AnyVersion = project,
    val active: Boolean = false,
    val path: Path
)

@Serializable @JvmRecord
public data class BranchInfo(
    val id: Identifier,
    val path: Path
)

@Serializable @JvmRecord
public data class NodeModel(
    val project: Identifier,
    val version: AnyVersion = project,
    val active: Boolean = false,
    val branch: BranchInfo,
    val root: Path,
    val parameters: ProcessParameters,
    val flags: ConfigurationRegistry = ConfigurationRegistry(),
    @SerialName("replacement_filters")
    val replacementFilters: Map<Int, ReplacementFilter> = emptyMap(),
)

@Serializable @JvmRecord
public data class BranchModel(
    val id: String,
    val root: Path,
    val nodes: List<NodeInfo>,
)

@Serializable @JvmRecord
public data class TreeModel(
    val stonecutter: String,
    val vcs: Identifier,
    val current: Identifier? = null,
    val branches: List<BranchInfo> = emptyList(),
    val nodes: List<NodeInfo> = emptyList(),
    val flags: ConfigurationRegistry = ConfigurationRegistry(),
)

public object PathSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("java.nio.file.Path", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Path): Unit = encoder.encodeString(value.invariantSeparatorsPathString)
    override fun deserialize(decoder: Decoder): Path = Path(decoder.decodeString())
}
