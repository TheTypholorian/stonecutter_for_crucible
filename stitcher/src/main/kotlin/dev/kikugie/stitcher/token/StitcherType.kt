package dev.kikugie.stitcher.token

import dev.kikugie.stitcher.antlr.StitcherLexer
import org.antlr.v4.runtime.Vocabulary
import java.util.concurrent.ConcurrentHashMap

public sealed interface StitcherType  {
    public val value: Int
    public val name: String
    public val source: TypeFlyweight

    public fun copy(type: Int): StitcherType = source[type]

    public interface TypeFlyweight {
        public operator fun get(type: Int): StitcherType
    }

    private class VocabTypeFlyweight(private val vocab: Vocabulary) : TypeFlyweight {
        private val cache: MutableMap<Int, StitcherType> = ConcurrentHashMap()
        override fun get(type: Int): StitcherType {
            assert(type in 1..vocab.maxTokenType) { "Type $type !in 1..${vocab.maxTokenType}" }
            return cache.computeIfAbsent(type, ::VocabStitcherType)
        }

        private inner class VocabStitcherType(override val value: Int) : StitcherType {
            override val name: String get() = vocab.getDisplayName(value)
            override val source: TypeFlyweight get() = this@VocabTypeFlyweight
        }
    }

    public companion object {
        @JvmField public val Leaf: TypeFlyweight = VocabTypeFlyweight(StitcherLexer.VOCABULARY)
    }

    public enum class Composite : StitcherType {
        ROOT, CODE, CONTENT, COMMENT,
        SWAP_OPEN, SWAP_CLOSE, COND_OPEN, COND_EXT, COND_CLOSE, REPL,
        GROUP, UNARY, BINARY, CONSTANT, ASSIGNMENT;

        override val value: Int get() = ordinal + 1
        override val source: TypeFlyweight get() = Companion

        public companion object : TypeFlyweight {
            override fun get(type: Int): Composite {
                assert(type in 1..entries.size) { "Type $type !in 1..${entries.size}" }
                return entries[type - 1]
            }
        }
    }

    public enum class Layout : StitcherType {
        CONTENT, COMMENT_OPEN, COMMENT_BODY, COMMENT_CLOSE;

        override val value: Int get() = ordinal + 1
        override val source: TypeFlyweight get() = Companion

        public companion object : TypeFlyweight {
            override fun get(type: Int): Layout {
                assert(type in 1..entries.size) { "Type $type !in 1..${entries.size}" }
                return entries[type - 1]
            }
        }
    }

    public enum class Custom : StitcherType {
        CLOSED_SCOPE, LOOKUP_SCOPE, PREDICATE;

        override val value: Int get() = ordinal + 1
        override val source: TypeFlyweight get() = Companion

        public companion object : TypeFlyweight {
            override fun get(type: Int): Custom {
                assert(type in 1..entries.size) { "Type $type !in 1..${entries.size}" }
                return entries[type - 1]
            }
        }
    }
}
