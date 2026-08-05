package dev.kikugie.stonecutter.controller.file

import dev.kikugie.stitcher.token.type.BuiltinTokenType
import dev.kikugie.stitcher.transformer.edit.CommentingStrategy
import dev.kikugie.stitcher.transformer.edit.impl.LineCommentingStrategy
import dev.kikugie.stitcher.transformer.edit.impl.StandardSwappingStrategy
import dev.kikugie.stitcher.transformer.edit.SwappingStrategy
import dev.kikugie.stitcher.transformer.edit.UncommentingStrategy
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class FileHandlerDsl

/**
 * Extension class for configuring how Stonecutter should process the associated file format.
 * @see FileHandlerContainer
 */
@FileHandlerDsl
public abstract class HandlerBuilder @Inject constructor(objects: ObjectFactory) : Named {
    /**Comment lexer configuration. Not reassignable - configure instead.*/
    public abstract val scanner: Property<ScannerBuilder>

    /**
     * Text commenting function.
     *
     * The function receives a text block and should return a new string,
     * commented according to the associated file format.
     */
    public abstract val commenter: Property<CommentingStrategy>

    /**
     * Text uncommenting function.
     *
     * The function receives a comment body and should reformat it if needed.
     * *In most cases the default value works well enough*.
     */
    public abstract val uncommenter: Property<UncommentingStrategy>

    /**
     * Swap formatting function.
     *
     * The function receives a text block and the value that it should be replaced with,
     * and output a formatted string. *In most cases the default value works well enough*.
     */
    public abstract val swapper: Property<SwappingStrategy>

    init {
        swapper.convention(StandardSwappingStrategy)
        uncommenter.convention(BASIC_UNCOMMENTER)
        scanner.value(objects.newInstance<ScannerBuilder>()).disallowChanges()
    }

    /**Copies all configurations of the [other] builder.*/
    public fun copy(other: HandlerBuilder) {
        scanner.get().copy(other.scanner.get())
        commenter.set(other.commenter.get())
        uncommenter.set(other.uncommenter.get())
        swapper.set(other.swapper.get())
    }

    /**Configures the [scanner] property.*/
    public fun scanner(action: Action<ScannerBuilder>) {
        action.execute(scanner.get())
    }

    /**Creates a [commenter] that prepends the given [prefix] on each line.*/
    public fun line(prefix: String): CommentingStrategy =
        LineCommentingStrategy(prefix)

    private companion object {
        /**Default uncommenter for line-comment formats: strips the comment opener token.*/
        val BASIC_UNCOMMENTER: UncommentingStrategy = UncommentingStrategy { tracker, tokens ->
            for (token in tokens) if (token.type == BuiltinTokenType.COMMENT_OPEN)
                tracker.delete(token)
        }
    }
}