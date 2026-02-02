package dev.kikugie.stonecutter.build.config

import dev.kikugie.stitcher.replacement.RegexReplacement
import dev.kikugie.stitcher.replacement.Replacement
import dev.kikugie.stitcher.replacement.StringReplacement
import dev.kikugie.stitcher.util.IDENTIFIER_PATTERN
import dev.kikugie.stitcher.util.isValidIdentifier
import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.build.config.ReplacementContainer.RegexReplacementSpec
import dev.kikugie.stonecutter.build.config.ReplacementContainer.StringReplacementSpec
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class ReplacementDsl

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class ReplacementSpecDsl

/**[Stonecutter replacement](https://stonecutter.kikugie.dev/wiki/config/params#replacements) configuration extension.*/
@ReplacementDsl
public sealed interface ReplacementContainer {
    /**Creates and configures a new [string replacement](https://stonecutter.kikugie.dev/wiki/config/params#string-replacements).*/
    public fun string(action: Action<StringReplacementSpec>)

    /**Creates and configures a new [string replacement](https://stonecutter.kikugie.dev/wiki/config/params#string-replacements).*/
    public fun string(direction: Boolean, action: Action<StringReplacementSpec>): Unit =
        string { this.direction.set(direction); action.execute(this) }

    /**Creates and configures a new [string replacement](https://stonecutter.kikugie.dev/wiki/config/params#string-replacements).*/
    public fun string(direction: Boolean, id: Identifier, action: Action<StringReplacementSpec>): Unit =
        string { this.direction.set(direction); this.id.set(id); action.execute(this) }

    /**Creates and configures a new [string replacement](https://stonecutter.kikugie.dev/wiki/config/params#string-replacements).*/
    @Deprecated("Use the direction-first variant", replaceWith = ReplaceWith("string(direction, id, action)"))
    public fun string(id: Identifier, direction: Boolean? = null, action: Action<StringReplacementSpec>): Unit =
        string { this.id.set(id); this.direction.set(direction); action.execute(this) }

    /**Creates and configures a new [regex replacement](https://stonecutter.kikugie.dev/wiki/config/params#string-replacements).*/
    public fun regex(action: Action<RegexReplacementSpec>)

    /**Creates and configures a new [regex replacement](https://stonecutter.kikugie.dev/wiki/config/params#string-replacements).*/
    public fun regex(direction: Boolean, action: Action<RegexReplacementSpec>): Unit =
        regex { this.direction.set(direction); action.execute(this) }

    /**Creates and configures a new [regex replacement](https://stonecutter.kikugie.dev/wiki/config/params#string-replacements).*/
    public fun regex(direction: Boolean, id: Identifier, action: Action<RegexReplacementSpec>): Unit =
        regex { this.direction.set(direction); this.id.set(id); action.execute(this) }

    /**Creates and configures a new [regex replacement](https://stonecutter.kikugie.dev/wiki/config/params#string-replacements).*/
    @Deprecated("Use the direction-first variant", replaceWith = ReplaceWith("regex(direction, id, action)"))
    public fun regex(id: Identifier, direction: Boolean? = null, action: Action<RegexReplacementSpec>): Unit =
        regex { this.id.set(id); this.direction.set(direction); action.execute(this) }

    /**
     * [String replacement](https://stonecutter.kikugie.dev/wiki/config/params#string-replacements) configuration extension.
     *
     * Replacements are registered by using the [replace] function,
     * or implicitly if [from] and [to] properties are defined.
     */
    @ReplacementSpecDsl
    public interface StringReplacementSpec {
        /**
         * Specifies the direction of replaced values.
         *
         * When `true` - [from] is replaced with [to],
         * or in reverse otherwise.
         */
        public val direction: Property<Boolean>

        /**
         * Specifies the replacement identifiers.
         *
         * If not `null`, replacement will require a
         * `~ id` comment to be effective for the file.
         */
        public val id: Property<Identifier>

        /**The searched pattern. Must not be empty.*/
        public val from: Property<String>

        /**The replaced value. Must not be empty.*/
        public val to: Property<String>

        /**
         * Registers a replacement with [from] and [to] **function parameters**,
         * using the [direction] and [id] defined in the extension.
         * Can be called multiple times.
         */
        public fun replace(from: String, to: String)
    }

    /**
     * [Regex replacement](https://stonecutter.kikugie.dev/wiki/config/params#regex-replacements) configuration extension.
     *
     * Replacements are registered by using the [replace] function,
     * or implicitly if pattern properties are defined.
     */
    @ReplacementSpecDsl
    public interface RegexReplacementSpec {
        /**
         * Specifies the direction of replaced values.
         *
         * When `true` - [fromPattern] is replaced with [toValue],
         * or [reversePattern] with [reverseValue] otherwise.
         */
        public val direction: Property<Boolean>

        /**
         * Specifies the replacement identifiers.
         *
         * If not `null`, replacement will require a
         * `~ id` comment to be effective for the file.
         */
        public val id: Property<Identifier>

        /**The searched regex pattern if [direction] is `true`. Must not be empty.*/
        public val fromPattern: Property<String>

        /**The replaced value if [direction] is `true`. Must not be empty.*/
        public val toValue: Property<String>

        /**The searched regex pattern if [direction] is `false`. Must not be empty.*/
        public val reversePattern: Property<String>

        /**The replaced value if [direction] is `false`. Must not be empty.*/
        public val reverseValue: Property<String>

        /**Assigns [fromPattern] and [toValue] properties from [from] and [to] parameters.*/
        @Deprecated("Use the combined function")
        public fun replace(from: String, to: String) {
            fromPattern.value(from).disallowChanges()
            toValue.value(to).disallowChanges()
        }

        /**Assigns [reversePattern] and [reverseValue] properties from [from] and [to] parameters.*/
        @Deprecated("Use the combined function")
        public fun reverse(from: String, to: String) {
            reversePattern.value(from).disallowChanges()
            reverseValue.value(to).disallowChanges()
        }

        /**
         * Registers a replacement with [direct] and [reverse] function parameters,
         * using the [direction] and [id] defined in the extension.
         * Can be called multiple times.
         */
        public fun replace(direct: Pair<String, String>, reverse: Pair<String, String>)

        /**
         * Registers a replacement with the function parameters,
         * using the [direction] and [id] defined in the extension.
         * Can be called multiple times.
         */
        public fun replace(directPattern: String, directReplacement: String, reversePattern: String, reverseReplacement: String): Unit =
            replace(directPattern to directReplacement, reversePattern to reverseReplacement)
    }
}

internal abstract class ReplacementContainerImpl @Inject constructor(
    val string: (StringReplacement) -> Unit,
    val regex: (RegexReplacement) -> Unit,
    val objects: ObjectFactory
) : ReplacementContainer {
    override fun string(action: Action<StringReplacementSpec>): Unit =
        objects.newInstance<StringSpecImpl>(string).also(action::execute).finalise()

    override fun regex(action: Action<RegexReplacementSpec>): Unit =
        objects.newInstance<RegexSpecImpl>(regex).also(action::execute).finalise()
}

internal abstract class StringSpecImpl @Inject constructor(val consumer: (StringReplacement) -> Unit) : StringReplacementSpec {
    override fun replace(from: String, to: String) {
        val tags = mutableSetOf<Replacement.Tag>()
        val id = id.orNull?.let {
            if (!it.startsWith('!')) return@let it
            tags += Replacement.Tag.DEFAULT_ENABLED
            it.removePrefix("!")
        }

        val replacement =
            if (direction.get()) StringReplacement(to, setOf(from), id, tags)
            else StringReplacement(from, setOf(to), id, tags)
        consumer(replacement)
    }

    fun finalise() {
        if (from.isPresent || to.isPresent) replace(from.get(), to.get())
    }
}

internal abstract class RegexSpecImpl @Inject constructor(val consumer: (RegexReplacement) -> Unit) : RegexReplacementSpec {
    override fun replace(direct: Pair<String, String>, reverse: Pair<String, String>) {
        val tags = mutableSetOf<Replacement.Tag>()
        val id = id.orNull?.let {
            if (!it.startsWith('!')) return@let it
            tags += Replacement.Tag.DEFAULT_ENABLED
            it.removePrefix("!")
        }

        val replacement =
            if (direction.get()) RegexReplacement(direct.second, direct.first, id, tags)
            else RegexReplacement(reverse.second, reverse.first, id, tags)
        consumer(replacement)
    }

    fun finalise() {
        if (fromPattern.isPresent || toValue.isPresent || reversePattern.isPresent || reverseValue.isPresent)
            replace(fromPattern.get() to toValue.get(), reversePattern.get() to reverseValue.get())
    }
}
