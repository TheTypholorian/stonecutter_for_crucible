import dev.kikugie.semver.data.Version
import dev.kikugie.stitcher.replacement.Replacement
import dev.kikugie.stitcher.transformer.TransformParameters
import dev.kikugie.stitcher.transformer.impl.StandardSwapStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import util.IMMEDIATE_THROW
import util.TransformParametersBuilder
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.measureTime

fun main(args: Array<String>) {
    val base = args.firstOrNull()?.let(::Path)
        ?: return

    val counter = Counter()
    for (project in base.listDirectoryEntries()) {
        val model = ProjectModel(project)
        for (node in model.nodes)
            model.process(node, counter)
    }

    println("Avg time: ${counter.avgDuration}, avg length: ${counter.avgLength}")
}

private val JSON = Json {
    ignoreUnknownKeys = true
}

private class ProjectModel(val directory: Path) {
    val nodes by lazy {
        directory.resolve("versions").listDirectoryEntries()
            .asSequence().mapNotNull(::mapNodes)
    }

    fun process(node: NodeModelStub, counter: Counter) {
        for (file in directory.resolve("src/main").walk()) {
            val contents = file.readText()
            val params = node.parameters.buildFor(file)
                ?: continue

            val took = try {
                measureTime {
                    dev.kikugie.stitcher.process(file, params, IMMEDIATE_THROW)
                }
            } catch (e: Exception) {
                println("Failed to process $file:\n$e")
                continue
            }

            println("Processed $file in $took")
            counter.append(contents.length, took)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun mapNodes(path: Path): NodeModelStub? {
        if (!path.isDirectory()) return null

        val file = path.resolve("build/stonecutter-cache/node.json")
        return if (file.notExists()) null
        else file.inputStream().use(JSON::decodeFromStream)
    }
}

private class Counter {
    private var count: Int = 0
    private var lengthSum: Long = 0L
    private var durationSum: Duration = Duration.ZERO

    val avgLength: Double
        get() = lengthSum / count.toDouble()

    val avgDuration: Duration
        get() = durationSum / count

    fun append(len: Int, dur: Duration) {
        if (dur == Duration.ZERO) return
        lengthSum += len
        durationSum += dur
        count++
    }
}

@Serializable
private data class NodeModelStub(
    val parameters: TransformParametersStub
)

@Serializable
private data class TransformParametersStub(
    val swaps: Map<String, String> = emptyMap(),
    val constants: Map<String, Boolean> = emptyMap(),
    val dependencies: Map<String, Version> = emptyMap(),
    val replacements: List<Replacement> = emptyList(),
) {
    fun buildFor(path: Path): TransformParameters? = when(path.extension) {
        "java" -> toJava()
        "kt" -> toKotlin()
        else -> null
    }

    fun toJava(): TransformParameters = TransformParameters(
        TransformParametersBuilder.Scanner.Java,
        TransformParametersBuilder.Commenter.JavaStar,
        TransformParametersBuilder.Uncommenter.Java,
        StandardSwapStrategy,

        swaps,
        constants,
        dependencies,
        replacements
    )

    fun toKotlin(): TransformParameters = TransformParameters(
        TransformParametersBuilder.Scanner.Java,
        TransformParametersBuilder.Commenter.JavaStar,
        TransformParametersBuilder.Uncommenter.Java,
        StandardSwapStrategy,

        swaps,
        constants,
        dependencies,
        replacements
    )
}