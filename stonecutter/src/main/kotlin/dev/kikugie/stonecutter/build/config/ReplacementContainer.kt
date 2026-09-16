@file:Suppress("DEPRECATION")

package dev.kikugie.stonecutter.build.config

import dev.kikugie.stitcher.replacement.toInt
import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.ReplacementID
import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import dev.kikugie.stonecutter.build.config.ReplacementContainer.*
import dev.kikugie.stonecutter.model.process.PerlReplacement
import dev.kikugie.stonecutter.model.process.RegexReplacement
import dev.kikugie.stonecutter.model.process.Replacement
import dev.kikugie.stonecutter.model.process.ReplacementFilter
import dev.kikugie.stonecutter.model.process.StringReplacement
import dev.kikugie.stonecutter.util.isValidIdentifier
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.newInstance
import org.intellij.lang.annotations.Language
import org.pcre4j.option.Pcre2CompileOption
import java.util.*
import java.util.regex.Pattern
import javax.inject.Inject

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class ReplacementDsl

@DslMarker @Retention(AnnotationRetention.BINARY)
private annotation class ReplacementSpecDsl

/**[Stonecutter replacement](https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements) configuration extension.*/
@ReplacementDsl
public sealed interface ReplacementContainer {
    /**
     * Defines new string replacements.
     *
     * @param action Configuration action for the replacement spec.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#string-replacements">String Replacement Documentation</a>
     */
    public fun string(action: Action<StringReplacementSpec>)

    /**
     * Defines new string replacements.
     *
     * @param id Replacement identifier used to toggle replacements throughout the file.
     * @param action Configuration action for the replacement spec.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#string-replacements">String Replacement Documentation</a>
     */
    public fun string(id: @ReplacementID Identifier, action: Action<StringReplacementSpec>): Unit =
        string { this.id.set(id); action.execute(this) }

    /**
     * Defines new string replacements.
     *
     * @param direction Switches which way the replacement parameters are used.
     * @param action Configuration action for the replacement spec.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#string-replacements">String Replacement Documentation</a>
     */
    public fun string(direction: Boolean, action: Action<StringReplacementSpec>): Unit =
        string { this.direction.set(direction); action.execute(this) }

    /**
     * Defines new string replacements.
     *
     * @param direction Switches which way the replacement parameters are used.
     * @param id Replacement identifier used to toggle replacements throughout the file.
     * @param action Configuration action for the replacement spec.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#string-replacements">String Replacement Documentation</a>
     */
    public fun string(direction: Boolean, id: @ReplacementID Identifier, action: Action<StringReplacementSpec>): Unit =
        string { this.direction.set(direction); this.id.set(id); action.execute(this) }

    /**
     * Defines new string replacements.
     *
     * @param direction Switches which way the replacement parameters are used.
     * @param id Replacement identifier used to toggle replacements throughout the file.
     * @param action Configuration action for the replacement spec.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#string-replacements">String Replacement Documentation</a>
     */
    @Deprecated("Use the direction-first variant `string(direction, id)`", replaceWith = ReplaceWith("string(direction, id, action)"))
    public fun string(id: @ReplacementID Identifier, direction: Boolean?, action: Action<StringReplacementSpec>): Unit =
        string { this.id.set(id); this.direction.set(direction); action.execute(this) }

    /**
     * Defines new regex replacements.
     *
     * @param action Configuration action for the replacement spec.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#regex-replacements">Regex Replacement Documentation</a>
     */
    public fun regex(action: Action<RegexReplacementSpec>)

    /**
     * Defines new regex replacements.
     *
     * @param id Replacement identifier used to toggle replacements throughout the file.
     * @param action Configuration action for the replacement spec.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#regex-replacements">Regex Replacement Documentation</a>
     */
    public fun regex(id: @ReplacementID Identifier, action: Action<RegexReplacementSpec>): Unit =
        regex { this.id.set(id); action.execute(this) }

    /**
     * Defines new regex replacements.
     *
     * @param direction Switches which way the replacement parameters are used.
     * @param action Configuration action for the replacement spec.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#regex-replacements">Regex Replacement Documentation</a>
     */
    public fun regex(direction: Boolean, action: Action<RegexReplacementSpec>): Unit =
        regex { this.direction.set(direction); action.execute(this) }

    /**
     * Defines new regex replacements.
     *
     * @param direction Switches which way the replacement parameters are used.
     * @param id Replacement identifier used to toggle replacements throughout the file.
     * @param action Configuration action for the replacement spec.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#regex-replacements">Regex Replacement Documentation</a>
     */
    public fun regex(direction: Boolean, id: @ReplacementID Identifier, action: Action<RegexReplacementSpec>): Unit =
        regex { this.direction.set(direction); this.id.set(id); action.execute(this) }

    /**
     * Defines new regex replacements.
     *
     * @param direction Switches which way the replacement parameters are used.
     * @param id Replacement identifier used to toggle replacements throughout the file.
     * @param action Configuration action for the replacement spec.
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#regex-replacements">Regex Replacement Documentation</a>
     */
    @Deprecated("Use the direction-first variant `regex(direction, id)`", replaceWith = ReplaceWith("regex(direction, id, action)"))
    public fun regex(id: @ReplacementID Identifier, direction: Boolean?, action: Action<RegexReplacementSpec>): Unit =
        regex { this.id.set(id); this.direction.set(direction); action.execute(this) }

    /**
     * Defines new PCRE replacements.
     *
     * @param action Configuration action for the replacement spec.
     * @see <a href="https://perldoc.perl.org/perlre">Perl Regular Expressions Documentation</a>
     */
    @StonecutterExperimentalAPI
    public fun perl(action: Action<PerlReplacementSpec>)

    /**
     * Defines new PCRE replacements.
     *
     * @param direction Switches which way the replacement parameters are used.
     * @param action Configuration action for the replacement spec.
     * @see <a href="https://perldoc.perl.org/perlre">Perl Regular Expressions Documentation</a>
     */
    @StonecutterExperimentalAPI
    public fun perl(direction: Boolean, action: Action<PerlReplacementSpec>): Unit =
        perl { this.direction.set(direction); action.execute(this) }

    /**
     * Defines new PCRE replacements.
     *
     * @param direction Switches which way the replacement parameters are used.
     * @param id Replacement identifier used to toggle replacements throughout the file.
     * @param action Configuration action for the replacement spec.
     * @see <a href="https://perldoc.perl.org/perlre">Perl Regular Expressions Documentation</a>
     */
    @StonecutterExperimentalAPI
    public fun perl(direction: Boolean, id: @ReplacementID Identifier, action: Action<PerlReplacementSpec>): Unit =
        perl { this.direction.set(direction); this.id.set(id); action.execute(this) }

    /**
     * String replacement configuration extension.
     *
     * String replacements must have a defined [direction] and optionally an [id].
     * Replaced values are added with the [replace] function or [from] and [to] properties.
     * Registered replacements only apply to the files matched by this spec's [PatternFilter].
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#string-replacements">String Replacement Documentation</a>
     */
    @ReplacementSpecDsl
    public abstract class StringReplacementSpec : PatternFilter {
        /**
         * Ordering of the replaced values.
         *
         * When:
         * - `true`: [from] is replaced with [to]
         * - `false`: [to] is replaced with [from]
         * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#string-replacements">String Replacement Documentation</a>
         */
        public abstract val direction: Property<Boolean>

        /**
         * Optional identifier for the replacement.
         *
         * When:
         * - `null`: replacement is always performed
         * - non-null: replacement must be explicitly enabled by the id
         * - prefixed with '!': replacement is active by default and can be disabled by the id
         *
         * The identifier must match `[_a-zA-Z][_\-a-zA-Z0-9]*`.
         * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#replacement-identifiers">Replacement Identifier Documentation</a>
         */
        @ReplacementID
        public abstract val id: Property<Identifier>

        /**
         * Original value to be replaced. Must not be empty.
         */
        @Deprecated("Use the `replace()` function")
        public abstract val from: Property<String>

        /**
         * Replacement value to use. Must not be empty.
         */
        @Deprecated("Use the `replace()` function")
        public abstract val to: Property<String>

        /**
         * Registers a replacement with [from] and [to] **function parameters**,
         * using the [direction] and [id] defined in the extension.
         * Can be called multiple times.
         */
        public abstract fun replace(from: String, to: String)
    }

    /**
     * Regex replacement configuration extension.
     *
     * Regex replacements must have a defined [direction] and optionally an [id].
     * Replaced values are added with the [replace] function or corresponding properties.
     * Registered replacements only apply to the files matched by this spec's [PatternFilter].
     * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#regex-replacements">Regex Replacement Documentation</a>
     */
    @ReplacementSpecDsl
    public abstract class RegexReplacementSpec : PatternFilter {
        /**
         * Selector for the replaced values.
         *
         * When:
         * - `true`: [fromPattern] is replaced with [toValue]
         * - `false`: [reversePattern] is replaced with [reverseValue]
         * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#regex-replacements">Regex Replacement Documentation</a>
         */
        public abstract val direction: Property<Boolean>

        /**
         * Optional identifier for the replacement.
         *
         * When:
         * - `null`: replacement is always performed
         * - non-null: replacement must be explicitly enabled by the id
         * - prefixed with '!': replacement is active by default and can be disabled by the id
         *
         * The identifier must match `[_a-zA-Z][_\-a-zA-Z0-9]*`.
         * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#replacement-identifiers">Replacement Identifier Documentation</a>
         */
        @ReplacementID
        public abstract val id: Property<Identifier>

        /**
         * Regex pattern to match against when replacing content in the forward direction.
         */
        @Deprecated("Use the `replace()` function")
        public abstract val fromPattern: Property<String>

        /**
         * Replacement value for forward direction regex matching.
         */
        @Deprecated("Use the `replace()` function")
        public abstract val toValue: Property<String>

        /**
         * Regex pattern for matching strings to be replaced when processing files in reverse direction.
         */
        @Deprecated("Use the `replace()` function")
        public abstract val reversePattern: Property<String>

        /**
         * The replacement value to use when performing reverse regex transformations.
         */
        @Deprecated("Use the `replace()` function")
        public abstract val reverseValue: Property<String>

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
        @Suppress("DeprecatedCallableAddReplaceWith")
        @Deprecated("Use the comma-separated version")
        public fun replace(direct: Pair<String, String>, reverse: Pair<String, String>): Unit =
            replace(direct.first, direct.second, reverse.first, reverse.second)

        /**
         * Registers a replacement, using the [direction] and [id] defined in the extension.
         * Can be called multiple times.
         *
         * Only one of the two pairs is used, selected by [direction].
         *
         * @param directPattern Pattern matched when [direction] is `true`
         * @param directReplacement Value replacing the [directPattern] matches
         * @param reversePattern Pattern matched when [direction] is `false`
         * @param reverseReplacement Value replacing the [reversePattern] matches
         * @param flags [Pattern] compilation flags applied to both patterns
         */
        @JvmOverloads
        public fun replace(
            @Language("RegExp") directPattern: String, directReplacement: String,
            @Language("RegExp") reversePattern: String, reverseReplacement: String,
            flags: Int = 0
        ): Unit = replace(
            directPattern.toPattern(flags), directReplacement,
            reversePattern.toPattern(flags), reverseReplacement
        )

        /**
         * Registers a replacement, using the [direction] and [id] defined in the extension.
         * Can be called multiple times.
         *
         * Only one of the two pairs is used, selected by [direction].
         *
         * @param directPattern Pattern matched when [direction] is `true`
         * @param directReplacement Value replacing the [directPattern] matches
         * @param reversePattern Pattern matched when [direction] is `false`
         * @param reverseReplacement Value replacing the [reversePattern] matches
         * @param options [RegexOption]s applied to both patterns
         */
        public fun replace(
            @Language("RegExp") directPattern: String, directReplacement: String,
            @Language("RegExp") reversePattern: String, reverseReplacement: String,
            vararg options: RegexOption
        ): Unit = replace(
            directPattern.toRegex(options.toSet()), directReplacement,
            reversePattern.toRegex(options.toSet()), reverseReplacement
        )

        /**
         * Registers a replacement, using the [direction] and [id] defined in the extension.
         * Can be called multiple times.
         *
         * Only one of the two pairs is used, selected by [direction].
         *
         * @param directPattern Pattern matched when [direction] is `true`
         * @param directReplacement Value replacing the [directPattern] matches
         * @param reversePattern Pattern matched when [direction] is `false`
         * @param reverseReplacement Value replacing the [reversePattern] matches
         */
        public fun replace(
            directPattern: Regex, directReplacement: String,
            reversePattern: Regex, reverseReplacement: String,
        ): Unit = replace(
            directPattern.toPattern(), directReplacement,
            reversePattern.toPattern(), reverseReplacement
        )

        /**
         * Registers a replacement, using the [direction] and [id] defined in the extension.
         * Can be called multiple times.
         *
         * Only one of the two pairs is used, selected by [direction].
         *
         * @param directPattern Pattern matched when [direction] is `true`
         * @param directReplacement Value replacing the [directPattern] matches
         * @param reversePattern Pattern matched when [direction] is `false`
         * @param reverseReplacement Value replacing the [reversePattern] matches
         */
        public abstract fun replace(
            directPattern: Pattern, directReplacement: String,
            reversePattern: Pattern, reverseReplacement: String,
        )
    }

    /**
     * PCRE replacement configuration extension.
     *
     * PCRE replacements must have a defined [direction] and optionally an [id].
     * Replaced values are added with the [replace] function or corresponding properties.
     * Registered replacements only apply to the files matched by this spec's [PatternFilter].
     * @see <a href="https://perldoc.perl.org/perlre">Perl Regular Expressions Documentation</a>
     */
    @ReplacementSpecDsl @StonecutterExperimentalAPI
    public abstract class PerlReplacementSpec : PatternFilter {
        /**
         * Selector for the replaced values.
         *
         * When:
         * - `true`: the direct pattern of each [replace] call is used
         * - `false`: the reverse pattern of each [replace] call is used
         */
        public abstract val direction: Property<Boolean>

        /**
         * Optional identifier for the replacement.
         *
         * When:
         * - `null`: replacement is always performed
         * - non-null: replacement must be explicitly enabled by the id
         * - prefixed with '!': replacement is active by default and can be disabled by the id
         *
         * The identifier must match `[_a-zA-Z][_\-a-zA-Z0-9]*`.
         * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements#replacement-identifiers">Replacement Identifier Documentation</a>
         */
        @ReplacementID
        public abstract val id: Property<Identifier>

        /**
         * Registers a replacement, using the [direction] and [id] defined in the extension.
         * Can be called multiple times.
         *
         * Only one of the two pairs is used, selected by [direction].
         *
         * @param directPattern Pattern matched when [direction] is `true`
         * @param directReplacement Value replacing the [directPattern] matches
         * @param reversePattern Pattern matched when [direction] is `false`
         * @param reverseReplacement Value replacing the [reversePattern] matches
         * @param options Compile options applied to both patterns; `UTF` is always enabled
         * @see <a href="https://perldoc.perl.org/perlre">Perl Regular Expressions Documentation</a>
         */
        public abstract fun replace(
            directPattern: String, directReplacement: String,
            reversePattern: String, reverseReplacement: String,
            vararg options: Pcre2CompileOption
        )
    }
}

internal abstract class ReplacementContainerImpl @Inject constructor(
    val replacements: ListProperty<Replacement>,
    val filters: MapProperty<Int, ReplacementFilter>,
    val objects: ObjectFactory
) : ReplacementContainer {
    private var count: Int = 0

    override fun string(action: Action<StringReplacementSpec>): Unit =
        objects.newInstance<StringSpecImpl>(this).also(action::execute).finalise()

    override fun regex(action: Action<RegexReplacementSpec>): Unit =
        objects.newInstance<RegexSpecImpl>(this).also(action::execute).finalise()

    @OptIn(StonecutterExperimentalAPI::class)
    override fun perl(action: Action<PerlReplacementSpec>): Unit =
        objects.newInstance<PerlSpecImpl>(this).also(action::execute).finalise()

    fun register(replacement: Replacement): Int {
        replacements.add(replacement)
        return count++
    }
}

internal abstract class StringSpecImpl @Inject constructor(val container: ReplacementContainerImpl) :
    StringReplacementSpec(), PatternFilter by PatternSetFilter() {
    private val indices: MutableList<Int> = mutableListOf()
    private var usedImmediateAnonymousAssignment: Boolean = false

    override fun replace(from: String, to: String) {
        val (id, enabled) = id.unwrap()
        if (id == null) usedImmediateAnonymousAssignment = true
        else require(id.isValidIdentifier()) { "Invalid replacement identifier '$id'" }

        val it =
            if (direction.get()) StringReplacement(from, to, id, enabled)
            else StringReplacement(to, from, id, enabled)
        indices += container.register(it)
    }

    fun finalise() {
        if (usedImmediateAnonymousAssignment && id.isPresent)
            error("String replacement id '${id.get()}' was assigned after replacements were registered")

        if (from.isPresent || to.isPresent) replace(from.get(), to.get())
        container.registerFilter(indices, this)
    }
}

internal abstract class RegexSpecImpl @Inject constructor(val container: ReplacementContainerImpl) :
    RegexReplacementSpec(), PatternFilter by PatternSetFilter() {
    private val indices: MutableList<Int> = mutableListOf()
    private var usedImmediateAnonymousAssignment: Boolean = false

    override fun replace(directPattern: Pattern, directReplacement: String, reversePattern: Pattern, reverseReplacement: String) {
        val (id, enabled) = id.unwrap()
        if (id == null) usedImmediateAnonymousAssignment = true
        else require(id.isValidIdentifier()) { "Invalid replacement identifier '$id'" }

        val it =
            if (direction.get()) RegexReplacement(directPattern.pattern(), directReplacement, directPattern.flags(), id, enabled)
            else RegexReplacement(reversePattern.pattern(), reverseReplacement, reversePattern.flags(), id, enabled)
        indices += container.register(it)
    }

    fun finalise() {
        if (usedImmediateAnonymousAssignment && id.isPresent)
            error("Regex replacement id '${id.get()}' was assigned after replacements were registered")

        if (fromPattern.isPresent || toValue.isPresent || reversePattern.isPresent || reverseValue.isPresent)
            replace(fromPattern.get(), toValue.get(), reversePattern.get(), reverseValue.get())
        container.registerFilter(indices, this)
    }
}

@OptIn(StonecutterExperimentalAPI::class)
internal abstract class PerlSpecImpl @Inject constructor(val container: ReplacementContainerImpl) :
    PerlReplacementSpec(), PatternFilter by PatternSetFilter() {
    private val indices: MutableList<Int> = mutableListOf()
    private var usedImmediateAnonymousAssignment: Boolean = false

    override fun replace(
        directPattern: String,
        directReplacement: String,
        reversePattern: String,
        reverseReplacement: String,
        vararg options: Pcre2CompileOption
    ) {
        val (id, enabled) = id.unwrap()
        if (id == null) usedImmediateAnonymousAssignment = true
        else require(id.isValidIdentifier()) { "Invalid replacement identifier '$id'" }

        val set = EnumSet.noneOf(Pcre2CompileOption::class.java)
            .apply { addAll(options.toList()) }
            .toInt()

        val it =
            if (direction.get()) PerlReplacement(directPattern, directReplacement, set, id, enabled)
            else PerlReplacement(reversePattern, reverseReplacement, set, id, enabled)
        indices += container.register(it)
    }

    fun finalise() {
        if (usedImmediateAnonymousAssignment && id.isPresent)
            error("Regex replacement id '${id.get()}' was assigned after replacements were registered")
        container.registerFilter(indices, this)
    }
}

private fun ReplacementContainerImpl.registerFilter(indices: List<Int>, spec: PatternFilter) {
    if (indices.isEmpty() || (spec.includes.isEmpty() && spec.excludes.isEmpty())) return
    val filter = ReplacementFilter(spec.includes.toSet(), spec.excludes.toSet())
    for (index in indices) filters.put(index, filter)
}

private fun Provider<Identifier>.unwrap(): Pair<String?, Boolean> {
    val id = orNull ?: return null to false
    return id.trimStart('!') to id.startsWith('!')
}
