package dev.kikugie.stitcher.parser.adapter

import dev.kikugie.commons.collections.FixedQueue
import dev.kikugie.commons.collections.first
import dev.kikugie.commons.collections.plusAssign
import dev.kikugie.commons.then
import dev.kikugie.stitcher.issue.ProblemID
import dev.kikugie.stitcher.issue.ProblemReporter
import dev.kikugie.stitcher.issue.location
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.parser.layout.LayoutParser
import dev.kikugie.stitcher.util.AntlrToken
import dev.kikugie.stitcher.util.indexedName
import dev.kikugie.stitcher.util.length
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.TokenSource
import org.antlr.v4.runtime.Vocabulary
import org.antlr.v4.runtime.misc.Pair
import java.io.Serializable

private val INCONSISTENT_TOKEN = ProblemID.SCANNER_GROUP.create("inconsistent-token")
private val UNREGISTERED_TOKEN = ProblemID.SCANNER_GROUP.create("unregistered-token")

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
public class ScannerAdapter(
    internal val scanner: Lexer,
    private val openers: IntArray,
    private val closers: IntArray,
    private val problems: ProblemReporter
) : TokenSource by scanner{

    /**
     * Encapsulates the creation of a [ScannerAdapter] configured with
     * a specific [Lexer], [openers][ScannerAdapter.openers] and [closers][ScannerAdapter.closers].
     */
    public fun interface Factory : Serializable {
        public fun create(input: CharStream, problems: ProblemReporter): ScannerAdapter
    }

    private data class Checkpoint(val cursor: Int, val line: Int, val offset: Int, val comment: Boolean)

    private val queue: FixedQueue<AntlrToken> = FixedQueue(4)
    private var checkpoint: Checkpoint = Checkpoint(0, 0, 0, false)

    private val scannerName: String by lazy {
        scanner.grammarFileName
            ?: scanner::class.qualifiedName?.takeIf { "org.antlr" !in it }
            ?: "<unknown>"
    }
    private val scannerVocab: Vocabulary
        inline get() = scanner.vocabulary

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
        if (checkpoint.comment) handleCommentEnd(token)
        else if (checkpoint.cursor < token.startIndex)
            push(LayoutParser.CONTENT, token.startIndex)
        queue += token
        checkpoint = Checkpoint(-1, -1, -1, false)
        return true
    }

    private fun handleCommentStart(token: AntlrToken): Boolean {
        if (checkpoint.comment)
            return reportInvalidOpener(token) then false
        if (checkpoint.cursor < token.startIndex)
            push(LayoutParser.CONTENT, token.startIndex)
        push(LayoutParser.COMMENT_OPEN, token)
        checkpoint = Checkpoint(token, true)
        return true
    }

    private fun handleCommentEnd(token: AntlrToken): Boolean {
        if (!checkpoint.comment)
            return reportInvalidCloser(token) then false
        push(LayoutParser.COMMENT_BODY, token.startIndex)
        push(LayoutParser.COMMENT_CLOSE, token)
        checkpoint = Checkpoint(token, false)
        return true
    }

    private fun reportInvalidOpener(token: AntlrToken) = problems.report(INCONSISTENT_TOKEN, token.location, token.length) {
        val options = closers.joinToString(", ", "[", "]") { scannerVocab.indexedName(it) }
        label = "Unexpected comment opener"
        details = "Comment scanner $scannerName provided token ${scannerVocab.indexedName(token)}, but $options were expected."
        documentation = "https://stonecutter.kikugie.dev/wiki/config/handlers#token-ordering"
    }

    private fun reportInvalidCloser(token: AntlrToken) = problems.report(INCONSISTENT_TOKEN, token.location, token.length) {
        val options = openers.joinToString(", ", "[", "]") { scannerVocab.indexedName(it) }
        label = "Unexpected comment closer"
        details = "Comment scanner $scannerName provided token ${scannerVocab.indexedName(token)}, but $options were expected."
        documentation = "https://stonecutter.kikugie.dev/wiki/config/handlers#token-ordering"
    }

    private fun reportUnknown(token: AntlrToken) = problems.report(UNREGISTERED_TOKEN, token.location, token.length) {
        label = "Unknown token type"
        details = "Comment scanner $scannerName provided token ${scannerVocab.indexedName(token)} which is not registered as an opener or a closer."
        documentation = "https://stonecutter.kikugie.dev/wiki/config/handlers#token-counting"
    }

    private fun push(type: Int, endExclusive: Int): Unit =
        push(type, checkpoint.cursor, endExclusive)

    private fun push(type: Int, start: Int, endExclusive: Int) {
        queue += tokenFactory.create(
            Pair(this, inputStream), type, null, AntlrToken.DEFAULT_CHANNEL,
            start, endExclusive - 1, checkpoint.line, checkpoint.offset
        )
    }

    private fun push(type: Int, host: AntlrToken) {
        queue += tokenFactory.create(
            Pair(this, inputStream), type, if (host.type == AntlrToken.EOF) "" else null, AntlrToken.DEFAULT_CHANNEL,
            host.startIndex, host.stopIndex, host.line, host.charPositionInLine
        )
    }

    private fun Checkpoint(token: AntlrToken, comment: Boolean): Checkpoint =
        Checkpoint(token.stopIndex + 1, scanner.line, scanner.charPositionInLine, comment)
}