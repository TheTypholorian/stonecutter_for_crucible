import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.nio.file.StandardOpenOption
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

abstract class GenerateVersionTask : DefaultTask() {
    @get:Input
    abstract val constants: MapProperty<String, Any>

    @get:OutputFile
    abstract val file: RegularFileProperty

    @TaskAction
    fun run() {
        val module = file.asFile.get().parent.substringAfter("kotlin/").replace('/', '.')
        val declarations = constants.get().entries.joinToString("\n") { (name, value) -> defineConst(value, name) }
        with(file.asFile.get().toPath()) {
            createParentDirectories()
            writeText("package $module\n$declarations", Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        }
    }

    private fun defineConst(value: Any, name: String): CharSequence = when (value) {
        is Int -> "internal const val $name: Int = $value"
        else -> "internal const val $name: String = \"$value\""
    }
}
