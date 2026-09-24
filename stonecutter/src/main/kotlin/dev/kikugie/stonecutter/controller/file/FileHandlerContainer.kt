package dev.kikugie.stonecutter.controller.file

import dev.kikugie.stonecutter.data.container.GradleContainerService
import dev.kikugie.stonecutter.util.newInstance
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.setProperty

/**
 * Extension class providing a registry for file format handlers.
 *
 * Registered handlers describe how Stonecutter should process a file of a given format.
 * File formats are associated by their lowercase extensions, without a dot prefix.
 *
 * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/custom-formats">File Handlers Documentation</a>
 */
public abstract class FileHandlerContainer : GradleContainerService {
    internal abstract val handlers: NamedDomainObjectContainer<HandlerBuilder>

    internal lateinit var service: Provider<FileHandlerService>

    internal val extensions: Provider<Set<String>> by lazy {
        objects.setProperty<String>().apply {
            set(service.map { it.handlers.keys })
            finalizeValueOnRead()
        }
    }

    /**Gets a file handler for the given extension or throws if it's not registered.*/
    public fun named(extension: String): NamedDomainObjectProvider<HandlerBuilder> =
        handlers.named(extension)

    /**
     * Copies the configuration of the [from] extension to the provided [extensions].
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/custom-formats#configuration-reuse">Configuration Reuse Documentation</a>
     */
    public fun inherit(from: String, vararg extensions: String) {
        val parent = named(from).get()
        for (it in extensions) handlers.make(it) { copy(parent) }
    }

    /**
     * Registers or updates the configurations for the given [extensions].
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/custom-formats#handler-registration">Handler Registration Documentation</a>
     */
    public fun configure(vararg extensions: String, configuration: Action<HandlerBuilder>) {
        for (it in extensions) handlers.make(it, configuration)
    }

    internal fun build(): Map<String, HandlerData> = handlers.asMap.mapValues { (_, it) ->
        it.toSerializableData(objects)
    }

    private fun <T : Any> NamedDomainObjectContainer<T>.make(name: String, config: Action<T>) {
        if (name in names) named(name, config) else create(name, config)
    }
}

private fun HandlerBuilder.toSerializableData(objects: ObjectFactory): HandlerData = objects.newInstance {
    val scanner = this@toSerializableData.scanner.get()
    openers.set(scanner.openers.get())
    closers.set(scanner.closers.get())
    lexer.set(scanner.lexer.get())
    commenter.set(this@toSerializableData.commenter.get())
    uncommenter.set(this@toSerializableData.uncommenter.get())
    swapper.set(this@toSerializableData.swapper.get())
}
