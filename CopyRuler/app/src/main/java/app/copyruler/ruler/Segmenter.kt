package app.copyruler.ruler

import java.text.BreakIterator
import java.util.Locale

/** How the source text is cut into pieces ("ท่อน") for copying. */
enum class SegmentUnit { LINE, PHRASE }

/**
 * One piece of the source text.
 *
 * @property text the trimmed text shown to the user.
 * @property start inclusive offset of [text] in the source.
 * @property end exclusive offset of [text] in the source.
 * @property line index of the source line the piece comes from.
 */
data class Segment(val text: String, val start: Int, val end: Int, val line: Int)

/**
 * Splits source text into segments, either one per line or one per phrase.
 *
 * Phrase mode cuts after spaces and after CJK-style punctuation, then merges short
 * pieces until each is at least [MIN_PHRASE_LENGTH] visible characters long. That
 * works for Thai, which puts spaces between phrases rather than between words, as
 * well as for English, where the merge step groups words into readable chunks.
 * Phrases never cross line breaks.
 */
object Segmenter {
    /** Phrases shorter than this many visible characters are merged with the next piece. */
    const val MIN_PHRASE_LENGTH = 18

    /** Pieces without a single space that are longer than this are cut at word boundaries. */
    const val MAX_UNBROKEN_LENGTH = 60

    /** Target length for pieces cut out of a long unbroken run. */
    private const val LONG_RUN_TARGET = 30

    /** Punctuation that ends a piece even when no space follows (CJK text has no spaces). */
    private const val HARD_BREAKS = "，。、；：！？…"

    /** Closing marks that stay attached to the punctuation before them. */
    private const val CLOSING_MARKS = "」』）】》〉”’\"')]}"

    private val THAI: Locale = Locale.forLanguageTag("th")

    fun split(
        text: String,
        unit: SegmentUnit,
        minPhraseLength: Int = MIN_PHRASE_LENGTH,
    ): List<Segment> {
        val out = ArrayList<Segment>()
        var lineStart = 0
        var line = 0
        while (true) {
            val newline = text.indexOf('\n', lineStart)
            val lineEnd = if (newline == -1) text.length else newline
            when (unit) {
                SegmentUnit.LINE -> trimmed(text, lineStart, lineEnd)?.let { (start, end) ->
                    out += Segment(text.substring(start, end), start, end, line)
                }
                SegmentUnit.PHRASE ->
                    splitLine(text, lineStart, lineEnd, line, minPhraseLength, out)
            }
            if (newline == -1) break
            lineStart = newline + 1
            line++
        }
        return out
    }

    /**
     * Index of the segment that contains [offset], used to keep the reader's place when
     * switching between line and phrase mode. Offsets past the end map to the last segment.
     */
    fun indexForOffset(segments: List<Segment>, offset: Int): Int {
        if (segments.isEmpty()) return 0
        val index = segments.indexOfFirst { offset < it.end }
        return if (index == -1) segments.lastIndex else index
    }

    /**
     * Number of characters that take up their own space on screen. Thai vowel and tone
     * marks sit above or below the previous letter, so they are not counted.
     */
    fun visibleLength(text: CharSequence, start: Int = 0, end: Int = text.length): Int {
        var count = 0
        var i = start
        while (i < end) {
            val codePoint = Character.codePointAt(text, i)
            if (takesSpace(codePoint)) count++
            i += Character.charCount(codePoint)
        }
        return count
    }

    private fun takesSpace(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        Character.FORMAT.toInt() -> false
        else -> true
    }

    private fun splitLine(
        text: String,
        lineStart: Int,
        lineEnd: Int,
        line: Int,
        minLength: Int,
        out: MutableList<Segment>,
    ) {
        val (start, end) = trimmed(text, lineStart, lineEnd) ?: return
        val firstOfLine = out.size
        var chunkStart = -1
        var chunkEnd = -1
        for ((pieceStart, pieceEnd) in pieces(text, start, end)) {
            if (chunkStart == -1) chunkStart = pieceStart
            chunkEnd = pieceEnd
            val (s, e) = trimmed(text, chunkStart, chunkEnd) ?: continue
            if (visibleLength(text, s, e) >= minLength) {
                out += Segment(text.substring(s, e), s, e, line)
                chunkStart = -1
            }
        }
        if (chunkStart == -1) return
        val (s, e) = trimmed(text, chunkStart, chunkEnd) ?: return
        val tinyLeftover = visibleLength(text, s, e) < minLength / 2
        if (tinyLeftover && out.size > firstOfLine) {
            // A few characters left at the end of a line read better with the phrase before them.
            val previous = out.removeAt(out.lastIndex)
            out += Segment(text.substring(previous.start, e), previous.start, e, line)
        } else {
            out += Segment(text.substring(s, e), s, e, line)
        }
    }

    /** Cuts the trimmed range [start, end) into pieces; trailing spaces stay with each piece. */
    private fun pieces(text: String, start: Int, end: Int): List<Pair<Int, Int>> {
        val result = ArrayList<Pair<Int, Int>>()
        var pieceStart = start
        for (i in start + 1 until end) {
            val previous = text[i - 1]
            val current = text[i]
            val afterSpace = previous.isWhitespace() && !current.isWhitespace()
            val afterHardBreak = previous in HARD_BREAKS && !current.isWhitespace() &&
                current !in HARD_BREAKS && current !in CLOSING_MARKS
            if (afterSpace || afterHardBreak) {
                result += splitLongRun(text, pieceStart, i)
                pieceStart = i
            }
        }
        result += splitLongRun(text, pieceStart, end)
        return result
    }

    /** Thai is often written in long runs without spaces; cut those at word boundaries. */
    private fun splitLongRun(text: String, start: Int, end: Int): List<Pair<Int, Int>> {
        if (visibleLength(text, start, end) <= MAX_UNBROKEN_LENGTH) return listOf(start to end)
        val words = BreakIterator.getWordInstance(THAI)
        words.setText(text.substring(start, end))
        val result = ArrayList<Pair<Int, Int>>()
        var cutStart = start
        var boundary = words.next()
        while (boundary != BreakIterator.DONE && start + boundary < end) {
            val position = start + boundary
            if (visibleLength(text, cutStart, position) >= LONG_RUN_TARGET) {
                result += cutStart to position
                cutStart = position
            }
            boundary = words.next()
        }
        if (result.isNotEmpty() && visibleLength(text, cutStart, end) < LONG_RUN_TARGET / 2) {
            val last = result.removeAt(result.lastIndex)
            result += last.first to end
        } else {
            result += cutStart to end
        }
        return result
    }

    /** Range without surrounding whitespace or zero-width characters, or null if nothing is left. */
    private fun trimmed(text: String, start: Int, end: Int): Pair<Int, Int>? {
        var s = start
        var e = end
        while (s < e && isTrimmable(text[s])) s++
        while (e > s && isTrimmable(text[e - 1])) e--
        return if (s < e) s to e else null
    }

    private fun isTrimmable(c: Char): Boolean =
        c.isWhitespace() || c == '​' || c == '‌' || c == '‍' || c == '﻿'
}
