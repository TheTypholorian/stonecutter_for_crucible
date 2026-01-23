package dev.kikugie.stitcher.token

import dev.kikugie.stitcher.antlr.StitcherLexer
import org.antlr.v4.runtime.Vocabulary
import java.util.concurrent.ConcurrentHashMap

sealed interface StitcherType  {
    val value: Int
    val name: String
    val source: TypeFlyweight

    fun copy(type: Int): StitcherType = source[type]

    interface TypeFlyweight {
        operator fun get(type: Int): StitcherType
        fun getOrNull(type: Int): StitcherType?
    }

    private class VocabTypeFlyweight(private val vocab: Vocabulary) : TypeFlyweight {
        private val cache: MutableMap<Int, StitcherType> = ConcurrentHashMap()
        override fun get(type: Int): StitcherType {
            assert(type in 1..vocab.maxTokenType) { "Type $type !in 1..${vocab.maxTokenType}" }
            return cache.computeIfAbsent(type, ::VocabStitcherType)
        }

        override fun getOrNull(type: Int): StitcherType? =
            if (type !in 1..vocab.maxTokenType) null
            else cache.computeIfAbsent(type, ::VocabStitcherType)

        private inner class VocabStitcherType(override val value: Int) : StitcherType {
            override val name: String get() = vocab.getDisplayName(value)
            override val source: TypeFlyweight get() = this@VocabTypeFlyweight
        }
    }

    companion object {
        @JvmField val Leaf: TypeFlyweight = VocabTypeFlyweight(StitcherLexer.VOCABULARY)
    }

    enum class Composite : StitcherType {
        ROOT, CODE, CONTENT, COMMENT,
        SWAP_OPEN, SWAP_CLOSE, COND_OPEN, COND_EXT, COND_CLOSE, REPL,
        GROUP, UNARY, BINARY, CONSTANT, ASSIGNMENT;

        override val value: Int get() = ordinal + 1
        override val source: TypeFlyweight get() = Companion

        companion object : TypeFlyweight {
            override fun get(type: Int): Composite {
                assert(type in 1..entries.size) { "Type $type !in 1..${entries.size}" }
                return entries[type - 1]
            }

            override fun getOrNull(type: Int): StitcherType? =
                if (type !in 1..entries.size) null
                else entries[type - 1]
        }
    }

    enum class Layout : StitcherType {
        CONTENT, COMMENT_OPEN, COMMENT_BODY, COMMENT_CLOSE;

        override val value: Int get() = ordinal + 1
        override val source: TypeFlyweight get() = Companion

        companion object : TypeFlyweight {
            override fun get(type: Int): Layout {
                assert(type in 1..entries.size) { "Type $type !in 1..${entries.size}" }
                return entries[type - 1]
            }

            override fun getOrNull(type: Int): StitcherType? =
                if (type !in 1..entries.size) null
                else entries[type - 1]
        }
    }

    enum class Custom : StitcherType {
        CLOSED_SCOPE, LOOKUP_SCOPE, PREDICATE;

        override val value: Int get() = ordinal + 1
        override val source: TypeFlyweight get() = Companion

        companion object : TypeFlyweight {
            override fun get(type: Int): Custom {
                assert(type in 1..entries.size) { "Type $type !in 1..${entries.size}" }
                return entries[type - 1]
            }

            override fun getOrNull(type: Int): StitcherType? =
                if (type !in 1..entries.size) null
                else entries[type - 1]
        }
    }
}
