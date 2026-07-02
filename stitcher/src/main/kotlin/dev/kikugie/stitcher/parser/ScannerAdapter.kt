package dev.kikugie.stitcher.parser

import dev.kikugie.commons.collections.FixedQueue
import dev.kikugie.commons.then
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.endIndex
import dev.kikugie.stitcher.antlr.indexedName
import dev.kikugie.stitcher.document.locate
import dev.kikugie.stitcher.issue.ProblemID
import dev.kikugie.stitcher.issue.ProblemReporter
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.token.NativeStitcherType.*
import dev.kikugie.stitcher.token.StitcherType
import dev.kikugie.stitcher.util.create
import org.antlr.v4.runtime.*
import java.io.Serializable

private val SCANNER_GROUP: ProblemID = ProblemID("scanner-adapter")

/**
 * Adapts the output of a user-defined comment [Lexer],
 * providing a consistent stream of comment states.
 *
 * The provided [scanner] **must** only output comment opener and closer tokens.
 * ```kotlin
 *-/*? if >=1.0*/println("Using release")
 * ><>--------<><>----------------------<
 * | |         | \- resolved by adapter as content
 * | |         \- returned by scanner as closer
 * | \- resolved by adapter as comment body
 * \- returned by scanner as opener
 * ```
 *
 * The comment opener and closer token types must be provided in [openers] and [closers] arrays.
 * For line comments the line break (`'\r'|'\n'|'\r\n'`) should be the closer value.
 * The scanner implementation should make use of lexer states to ensure the token stream validity -
 * **opener tokens must only be followed by a single closer token**.
 * ```kotlin
 * /* /*  */ */
 * ---><-->< skipped
 * ```
 *
 * The scanner should `-> skip` irrelevant characters instead of sending them to `-> channel(HIDDEN)`,
 * as they are never used by the adapter, but can cause performance issues due to the [AntlrToken] object creation.
 *
 * Registered scanners should provide the respective adapter with a [Factory] implementation.
 */
class ScannerAdapter(
    internal val scanner: Lexer,
    private val openers: IntArray,
    private val closers: IntArray,
    private val problems: ProblemReporter
) : TokenSource by scanner {
    /**
     * Encapsulates the creation of a [ScannerAdapter] configured with
     * a specific [Lexer], [openers][ScannerAdapter.openers] and [closers][ScannerAdapter.closers].
     */
    fun interface Factory : Serializable {
        fun create(input: CharStream, problems: ProblemReporter): ScannerAdapter
    }

    private val queue: FixedQueue<AntlrToken> = FixedQueue(4)
    private val scannerName: String by lazy {
        scanner.grammarFileName
            ?: scanner::class.qualifiedName?.takeIf { "org.antlr" !in it }
            ?: "<unknown>"
    }
    private val scannerVocab: Vocabulary
        inline get() = scanner.vocabulary
    private lateinit var checkpoint: CommonToken

    override fun getLine(): Int =
        if (scanner._hitEOF) scanner.line else queue.peek()?.line ?: 0

    override fun getCharPositionInLine(): Int =
        if (scanner._hitEOF) scanner.charPositionInLine else queue.peek()?.charPositionInLine ?: 0

    override fun nextToken(): AntlrToken = when (queue.size) {
        0 -> advance() then queue.first()
        1 -> advance() then next()
        else -> next()
    }

    private fun next(): AntlrToken = queue.run { remove(); first() }

    private fun advance() {
        if (!::checkpoint.isInitialized) checkpoint = tokenFactory
            .create(scanner, inputStream, CONTENT, 0, 0, 1, 0) as CommonToken
        if (checkpoint.line < 0) throw NoSuchElementException()
        do {
            val next = scanner.nextToken()
            val consumed = when (next.type) {
                AntlrToken.EOF -> handleEOF(next)
                in openers -> handleCommentStart(next)
                in closers -> handleCommentEnd(next)
                else -> reportUnknown(next) then false
            }
        } while (!consumed)
    }

    private fun handleEOF(token: AntlrToken): Boolean {
        if (checkpoint.isComment) handleCommentEnd(token)
        else token.pushCheckpoint(COMMENT_BODY)
        checkpoint.line = -1
        queue += token
        return true
    }

    private fun handleCommentStart(token: AntlrToken): Boolean {
        if (checkpoint.isComment)
            return reportInvalidOpener(token) then false
        token.pushCheckpoint(COMMENT_BODY)
        token.pushAs(COMMENT_OPEN)
        return true
    }

    private fun handleCommentEnd(token: AntlrToken): Boolean {
        if (!checkpoint.isComment)
            return reportInvalidCloser(token) then false
        token.pushCheckpoint(CONTENT)
        token.pushAs(COMMENT_CLOSE)
        return true
    }

    private fun reportInvalidOpener(token: AntlrToken) = problems.report(SCANNER_GROUP + "inconsistent-token") {
        loc = doc.locate(token)
        label = "Unexpected comment opener"

        val options = closers.joinToString(", ", "[", "]") { scannerVocab.indexedName(it) }
        info = "Comment scanner $scannerName provided token ${scannerVocab.indexedName(token)}, but $options were expected."
        docs = "https://stonecutter.kikugie.dev/wiki/config/handlers#token-ordering"
    }

    private fun reportInvalidCloser(token: AntlrToken) = problems.report(SCANNER_GROUP + "inconsistent-token") {
        loc = doc.locate(token)
        label = "Unexpected comment closer"

        val options = openers.joinToString(", ", "[", "]") { scannerVocab.indexedName(it) }
        info = "Comment scanner $scannerName provided token ${scannerVocab.indexedName(token)}, but $options were expected."
        docs = "https://stonecutter.kikugie.dev/wiki/config/handlers#token-ordering"
    }

    private fun reportUnknown(token: AntlrToken) = problems.report(SCANNER_GROUP + "unregistered-token") {
        label = "Unknown token type"

        info = "Comment scanner $scannerName provided token ${scannerVocab.indexedName(token)} which is not registered as an opener or a closer."
        docs = "https://stonecutter.kikugie.dev/wiki/config/handlers#token-counting"
    }

    private fun AntlrToken.pushCheckpoint(type: StitcherType) {
        if (type == CONTENT || checkpoint.startIndex < startIndex) queue += checkpoint.apply {
            endIndex = this@pushCheckpoint.startIndex
            text = null
        }
        checkpoint = CommonToken(this).apply {
            this.type = type.value
            startIndex = this@pushCheckpoint.endIndex
            line = scanner.line
            charPositionInLine = scanner.charPositionInLine
        }
    }

    private fun AntlrToken.pushAs(type: StitcherType) {
        queue += CommonToken(this).apply {
            this.type = type.value
            if (this@pushAs.type == AntlrToken.EOF) text = ""
        }
    }

    private val AntlrToken.isComment: Boolean
        inline get() = type == COMMENT_BODY.value
}