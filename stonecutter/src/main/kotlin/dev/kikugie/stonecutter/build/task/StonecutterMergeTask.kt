package dev.kikugie.stonecutter.build.task

import dev.kikugie.stonecutter.StonecutterInternalAPI
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

/**
 * Un-bundles the generated sources back into the shared directory.
 */
@DisableCachingByDefault(because = "Writes into the shared source directories") @StonecutterInternalAPI
public abstract class StonecutterMergeTask : DefaultTask() {
    @get:[InputDirectory PathSensitive(PathSensitivity.RELATIVE)]
    public abstract val generated: DirectoryProperty

    @get:Internal
    public abstract val sourceRoots: MapProperty<String, File>

    @get:OutputDirectories
    internal val sourceDirectories: FileCollection
        get() = objects.fileCollection().from(sourceRoots.map { it.values })

    @get:Inject
    internal abstract val objects: ObjectFactory

    @get:Inject
    internal abstract val files: FileSystemOperations

    @TaskAction
    internal fun run() {
        val generated: File = generated.asFile.get()
        for ((root, dir) in sourceRoots.get()) files.copy {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
            from(generated.resolve(root))
            into(dir)
        }
    }
}
