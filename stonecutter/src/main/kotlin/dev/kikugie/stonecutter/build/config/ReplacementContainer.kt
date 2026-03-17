package dev.kikugie.stonecutter.build.config

import dev.kikugie.stitcher.replacement.RegexReplacement
import dev.kikugie.stitcher.replacement.Replacement
import dev.kikugie.stitcher.replacement.StringReplacement
import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.build.config.ReplacementContainer.RegexReplacementSpec
import dev.kikugie.stonecutter.build.config.ReplacementContainer.StringReplacementSpec
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.newInstance
import org.intellij.lang.annotations.Language
import javax.inject.Inject

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class ReplacementDsl

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class ReplacementSpecDsl

/**[Stonecutter replacement](https://stonecutter.kikugie.dev/wiki/config/params#replacements) configuration extension.*/
@ReplacementDsl
public sealed interface ReplacementContainer {
    /**
     * Defines a new string replacement with the [StringReplacementSpec].
     *
     * The configuration [action] must define the replacement
     * [direction][StringReplacementSpec.direction] in the lambda body.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#string-replacements">String Replacement Documentation</a>
     */
    public fun string(action: Action<StringReplacementSpec>)

    public fun string(id: Identifier, action: Action<StringReplacementSpec>): Unit =
        string { this.id.set(id); action.execute(this) }

    public fun string(direction: Boolean, action: Action<StringReplacementSpec>): Unit =
        string { this.direction.set(direction); action.execute(this) }

    public fun string(direction: Boolean, id: Identifier, action: Action<StringReplacementSpec>): Unit =
        string { this.direction.set(direction); this.id.set(id); action.execute(this) }

    @Deprecated("Use the direction-first variant `string(direction, id)`", replaceWith = ReplaceWith("string(direction, id, action)"))
    public fun string(id: Identifier, direction: Boolean?, action: Action<StringReplacementSpec>): Unit =
        string { this.id.set(id); this.direction.set(direction); action.execute(this) }

    public fun regex(action: Action<RegexReplacementSpec>)

    public fun regex(id: Identifier, action: Action<RegexReplacementSpec>): Unit =
        regex { this.id.set(id); action.execute(this) }

    public fun regex(direction: Boolean, action: Action<RegexReplacementSpec>): Unit =
        regex { this.direction.set(direction); action.execute(this) }

    public fun regex(direction: Boolean, id: Identifier, action: Action<RegexReplacementSpec>): Unit =
        regex { this.direction.set(direction); this.id.set(id); action.execute(this) }

    @Deprecated("Use the direction-first variant `regex(direction, id)`", replaceWith = ReplaceWith("regex(direction, id, action)"))
    public fun regex(id: Identifier, direction: Boolean?, action: Action<RegexReplacementSpec>): Unit =
        regex { this.id.set(id); this.direction.set(direction); action.execute(this) }

    /**
     * String replacement configuration extension.
     *
     * String replacements must have a defined [direction] and optionally an [id].
     * Replaced values are added with the [replace] function or [from] and [to] properties.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#string-replacements">String Replacement Documentation</a>
     */
    @ReplacementSpecDsl
    public interface StringReplacementSpec {
        /**
         * Ordering of the replaced values.
         *
         * When:
         * - `true`: [from] is replaced with [to]
         * - `false`: [to] is replaced with [from]
         * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#string-replacements">String Replacement Documentation</a>
         */
        public val direction: Property<Boolean>

        /**
         * Optional identifier for the replacement.
         *
         * When:
         * - `null`: replacement is always performed
         * - non-null: replacement must be explicitly enabled by the id
         * - prefixed with '!': replacement is active by default and can be disabled by the id
         *
         * The identifier must match `[_a-zA-Z][_\-a-zA-Z0-9]*.
         * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#replacement-identifiers">Replacement Identifier Documentation</a>
         */
        public val id: Property<Identifier>

        /**
         * Original value to be replaced. Must not be empty.
         */
        public val from: Property<String>

        /**
         * Replacement value to use. Must not be empty.
         */
        public val to: Property<String>

        /**
         * Registers a replacement with [from] and [to] **function parameters**,
         * using the [direction] and [id] defined in the extension.
         * Can be called multiple times.
         */
        public fun replace(from: String, to: String)
    }

    /**
     * Regex replacement configuration extension.
     *
     * Regex replacements must have a defined [direction] and optionally an [id].
     * Replaced values are added with the [replace] function or corresponding properties.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#regex-replacements">Regex Replacement Documentation</a>
     */
    @ReplacementSpecDsl
    public interface RegexReplacementSpec {
        /**
         * Selector for the replaced values.
         *
         * When:
         * - `true`: [fromPattern] is replaced with [toValue]
         * - `false`: [reversePattern] is replaced with [reverseValue]
         * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#regex-replacements">Regex Replacement Documentation</a>
         */
        public val direction: Property<Boolean>

        /**
         * Optional identifier for the replacement.
         *
         * When:
         * - `null`: replacement is always performed
         * - non-null: replacement must be explicitly enabled by the id
         * - prefixed with '!': replacement is active by default and can be disabled by the id
         *
         * The identifier must match `[_a-zA-Z][_\-a-zA-Z0-9]*.
         * @see <a href="https://stonecutter.kikugie.dev/wiki/config/params#replacement-identifiers">Replacement Identifier Documentation</a>
         */
        public val id: Property<Identifier>

        /**
         * Regex pattern to match against when replacing content in the forward direction.
         */
        public val fromPattern: Property<String>

        /**
         * Replacement value for forward direction regex matching.
         */
        public val toValue: Property<String>

        /**
         * Regex pattern for matching strings to be replaced when processing files in reverse direction.
         */
        public val reversePattern: Property<String>

        /**
         * The replacement value to use when performing reverse regex transformations.
         */
        public val reverseValue: Property<String>

        /**
         * Defines a regex replacement pattern for the direct transformation.
         *
         * @param from Regex pattern assigned to [fromPattern]
         * @param to Replacement value assigned to [toValue]
         */
        @Deprecated("Use the combined function `replace(pA, vA, pB, vB)`")
        public fun replace(from: String, to: String) {
            fromPattern.value(from).disallowChanges()
            toValue.value(to).disallowChanges()
        }

        /**
         * Defines a regex replacement pattern for the reverse transformation.
         *
         * @param from Regex pattern assigned to [reversePattern]
         * @param to Replacement value assigned to [reverseValue]
         */
        @Deprecated("Use the combined function `replace(pA, vA, pB, vB)`")
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
        public fun replace(
            @Language("RegExp") directPattern: String,
            directReplacement: String,
            @Language("RegExp") reversePattern: String,
            reverseReplacement: String
        ): Unit = replace(directPattern to directReplacement, reversePattern to reverseReplacement)
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
    private var usedImmediateAnonymousAssignment: Boolean = false

    override fun replace(from: String, to: String) {
        val tags = mutableSetOf<Replacement.Tag>()
        val id = id.unwrapWith(tags)
        if (id == null) usedImmediateAnonymousAssignment = true

        val replacement =
            if (direction.get()) StringReplacement(to, setOf(from), id, tags)
            else StringReplacement(from, setOf(to), id, tags)
        consumer(replacement)
    }

    fun finalise() {
        if (usedImmediateAnonymousAssignment && id.isPresent)
            error("String replacement id '${id.get()}' was assigned after replacements were registered")

        if (from.isPresent || to.isPresent) replace(from.get(), to.get())
    }
}

internal abstract class RegexSpecImpl @Inject constructor(val consumer: (RegexReplacement) -> Unit) : RegexReplacementSpec {
    private var usedImmediateAnonymousAssignment: Boolean = false

    override fun replace(direct: Pair<String, String>, reverse: Pair<String, String>) {
        val tags = mutableSetOf<Replacement.Tag>()
        val id = id.unwrapWith(tags)
        if (id == null) usedImmediateAnonymousAssignment = true

        val replacement =
            if (direction.get()) RegexReplacement(direct.second, direct.first, id, tags)
            else RegexReplacement(reverse.second, reverse.first, id, tags)
        consumer(replacement)
    }

    fun finalise() {
        if (usedImmediateAnonymousAssignment && id.isPresent)
            error("Regex replacement id '${id.get()}' was assigned after replacements were registered")

        if (fromPattern.isPresent || toValue.isPresent || reversePattern.isPresent || reverseValue.isPresent)
            replace(fromPattern.get() to toValue.get(), reversePattern.get() to reverseValue.get())
    }
}

private fun Provider<Identifier>.unwrapWith(tags: MutableSet<Replacement.Tag>): Identifier? = orNull?.let {
    if (!it.startsWith('!')) return@let it
    tags += Replacement.Tag.DEFAULT_ENABLED
    it.removePrefix("!")
}
