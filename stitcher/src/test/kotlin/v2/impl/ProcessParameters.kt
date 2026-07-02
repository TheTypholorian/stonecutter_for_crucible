package v2.impl

import dev.kikugie.stitcher.antlr.scanner.HashStyleScanner
import dev.kikugie.stitcher.antlr.scanner.HashStyleScanner.HASH_COMMENT_END
import dev.kikugie.stitcher.antlr.scanner.HashStyleScanner.HASH_COMMENT_START
import dev.kikugie.stitcher.antlr.scanner.SlashStyleScanner
import dev.kikugie.stitcher.antlr.scanner.SlashStyleScanner.*
import dev.kikugie.stitcher.parser.ScannerAdapter
import dev.kikugie.stitcher.transformer.impl.LineCommentStrategy
import dev.kikugie.stitcher.transformer.impl.SlashStarCommentStrategy
import dev.kikugie.stitcher.transformer.impl.StandardSwapStrategy
import dev.kikugie.stitcher.transformer.impl.StarRemoveCommentStrategy
import dev.kikugie.stitcher.transformer.strategy.CommentingStrategy
import dev.kikugie.stitcher.transformer.strategy.SwappingStrategy
import dev.kikugie.stitcher.transformer.strategy.UncommentingStrategy

object Scanners {
    val Java: ScannerAdapter.Factory = ScannerAdapter.Factory { input, problems ->
        val openers = intArrayOf(SLASH_COMMENT_START, STAR_COMMENT_START)
        val closers = intArrayOf(SLASH_COMMENT_END, STAR_COMMENT_END)
        ScannerAdapter(SlashStyleScanner(input), openers, closers, problems)
    }

    val Kotlin: ScannerAdapter.Factory = ScannerAdapter.Factory { input, problems ->
        val openers = intArrayOf(SLASH_COMMENT_START, STAR_COMMENT_START)
        val closers = intArrayOf(SLASH_COMMENT_END, STAR_COMMENT_END)
        ScannerAdapter(SlashStyleScanner(input).apply { nestMultiLineComments = true }, openers, closers, problems)
    }

    val Hash: ScannerAdapter.Factory = ScannerAdapter.Factory { input, problems ->
        val openers = intArrayOf(HASH_COMMENT_START)
        val closers = intArrayOf(HASH_COMMENT_END)
        ScannerAdapter(HashStyleScanner(input), openers, closers, problems)
    }
}

object Commenters {
    val JavaStar: CommentingStrategy = SlashStarCommentStrategy(true)
    val KotlinStar: CommentingStrategy = SlashStarCommentStrategy(false)

    val Hash: CommentingStrategy = LineCommentStrategy("#")
    val Slashes: CommentingStrategy = LineCommentStrategy("//")
}

object Uncommenters {
    val JavaLike: UncommentingStrategy = StarRemoveCommentStrategy

    val Lines: UncommentingStrategy = UncommentingStrategy { it, _, cl ->
        if ('\r' in cl || '\n' in cl) it + cl else it
    }
}

object Swappers {
    val Standard: SwappingStrategy = StandardSwapStrategy
}