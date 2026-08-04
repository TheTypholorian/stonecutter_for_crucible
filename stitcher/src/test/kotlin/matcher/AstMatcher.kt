package matcher

import dev.kikugie.stitcher.token.BlockToken
import dev.kikugie.stitcher.token.DefinitionToken
import io.kotest.matchers.collections.shouldBeSameSizeAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.reflect.KClass

@DslMarker
@Retention(AnnotationRetention.SOURCE)
private annotation class TokenMatcherDsl

private val DO_NOTHING: Any?.() -> Unit = {}

inline fun ast(builder: RootMatcher.() -> Unit): RootMatcher =
    RootMatcher().apply(builder)

@TokenMatcherDsl
sealed class BlockTokenMatcher {
    abstract fun test(token: BlockToken)
}

class ContentMatcher : BlockTokenMatcher() {
    var check: BlockToken.Content.() -> Unit = DO_NOTHING

    override fun test(token: BlockToken) {
        token.shouldBeInstanceOf<BlockToken.Content>(check)
    }
}

class CommentMatcher : BlockTokenMatcher() {
    var check: BlockToken.Comment.() -> Unit = DO_NOTHING

    override fun test(token: BlockToken) {
        token.shouldBeInstanceOf<BlockToken.Comment>(check)
    }
}

class RootMatcher : BlockTokenMatcher() {
    val entries: MutableList<BlockTokenMatcher> = ArrayList()

    inline fun <reified T : DefinitionToken> code(builder: CodeMatcher.() -> Unit = {}): CodeMatcher =
        CodeMatcher(T::class).apply(builder).also { entries += it }

    fun content(action: BlockToken.Content.() -> Unit = DO_NOTHING): ContentMatcher =
        ContentMatcher().apply { check = action }.also { entries += it }

    fun comment(action: BlockToken.Comment.() -> Unit = DO_NOTHING): CommentMatcher =
        CommentMatcher().apply { check = action }.also { entries += it }

    override fun test(token: BlockToken) {
        token.shouldBeInstanceOf<BlockToken.Root>()

        token.structure() shouldBe structure()
        for ((token, matcher) in token.scope.zip(entries))
            matcher.test(token)
    }
}

class CodeMatcher(val type: KClass<out DefinitionToken>) : BlockTokenMatcher() {
    var check: BlockToken.Code.() -> Unit = DO_NOTHING
    val entries: MutableList<BlockTokenMatcher> = ArrayList()

    inline fun <reified T : DefinitionToken> code(builder: CodeMatcher.() -> Unit = {}): CodeMatcher =
        CodeMatcher(T::class).apply(builder).also { entries += it }

    fun content(action: BlockToken.Content.() -> Unit = DO_NOTHING): ContentMatcher =
        ContentMatcher().apply { check = action }.also { entries += it }

    fun comment(action: BlockToken.Comment.() -> Unit = DO_NOTHING): CommentMatcher =
        CommentMatcher().apply { check = action }.also { entries += it }

    fun check(action: BlockToken.Code.() -> Unit) {
        check = action
    }

    override fun test(token: BlockToken) {
        token.shouldBeInstanceOf<BlockToken.Code>()
        token.scope shouldBeSameSizeAs entries
        for ((token, matcher) in token.scope.zip(entries))
            matcher.test(token)
    }
}

/**Structural view of a block token tree, used to compare the shape of the received and expected trees.*/
private sealed interface Structure {
    data object Content : Structure
    data object Comment : Structure
    data class Code(val type: String?, val scope: List<Structure>) : Structure
    data class Root(val scope: List<Structure>) : Structure
}

private fun BlockToken.structure(): Structure = when (this) {
    is BlockToken.Content -> Structure.Content
    is BlockToken.Comment -> Structure.Comment
    is BlockToken.Code -> Structure.Code(definition::class.simpleName, scope.map { it.structure() })
    is BlockToken.Root -> Structure.Root(scope.map { it.structure() })
}

private fun BlockTokenMatcher.structure(): Structure = when (this) {
    is ContentMatcher -> Structure.Content
    is CommentMatcher -> Structure.Comment
    is CodeMatcher -> Structure.Code(type.simpleName, entries.map { it.structure() })
    is RootMatcher -> Structure.Root(entries.map { it.structure() })
}
