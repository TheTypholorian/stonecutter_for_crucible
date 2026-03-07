import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.nio.file.StandardOpenOption
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

abstract class GenerateVersionTask : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:OutputFile
    abstract val file: RegularFileProperty

    @TaskAction
    fun run() {
        val module = file.asFile.get().parent.substringAfter("kotlin/").replace('/', '.')
        val template = """
            package $module
            internal const val __VERSION: String = "${version.get()}"
        """.trimIndent()
        with(file.asFile.get().toPath()) {
            createParentDirectories()
            writeText(template, Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        }
    }
}