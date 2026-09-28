package app.copyruler.ruler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.text.BreakIterator
import java.util.Locale

class SegmenterTest {

    private fun texts(text: String, unit: SegmentUnit, minLength: Int = Segmenter.MIN_PHRASE_LENGTH) =
        Segmenter.split(text, unit, minLength).map { it.text }

    // ---- Line mode -------------------------------------------------------------------------

    @Test
    fun lineMode_english_trimsAndSkipsBlankLines() {
        val source = "Hello world\n\n   Second line   \n\t\nThird"

        val segments = Segmenter.split(source, SegmentUnit.LINE)

        assertEquals(listOf("Hello world", "Second line", "Third"), segments.map { it.text })
        assertEquals(listOf(0, 2, 4), segments.map { it.line })
    }

    @Test
    fun lineMode_thai_handlesWindowsLineEndings() {
        val source = "พันธะเคมี\r\nพันธะไอออนิก\r\n\r\nพันธะโคเวเลนต์\r\n"

        assertEquals(
            listOf("พันธะเคมี", "พันธะไอออนิก", "พันธะโคเวเลนต์"),
            texts(source, SegmentUnit.LINE),
        )
    }

    @Test
    fun lineMode_keepsLongLineWhole() {
        val line = "พันธะเคมีคือแรงยึดเหนี่ยวระหว่างอะตอม ที่ทำให้อะตอมรวมกันเป็นโมเลกุลหรือสารประกอบ"

        assertEquals(listOf(line), texts(line, SegmentUnit.LINE))
    }

    @Test
    fun emptyOrBlankText_hasNoSegments() {
        for (unit in SegmentUnit.entries) {
            assertEquals(emptyList<Segment>(), Segmenter.split("", unit))
            assertEquals(emptyList<Segment>(), Segmenter.split(" \n\t\r\n ​ ", unit))
        }
    }

    // ---- Phrase mode: English --------------------------------------------------------------

    @Test
    fun phraseMode_english_groupsWordsToMinimumLength() {
        val source = "The quick brown fox jumps over the lazy dog. It barked."

        assertEquals(
            listOf("The quick brown fox", "jumps over the lazy", "dog. It barked."),
            texts(source, SegmentUnit.PHRASE),
        )
    }

    @Test
    fun phraseMode_english_tinyLeftoverJoinsPreviousPhrase() {
        // "Metals conduct heat" already reaches the minimum; "well." alone would be too short.
        assertEquals(
            listOf("Metals conduct heat well."),
            texts("Metals conduct heat well.", SegmentUnit.PHRASE),
        )
        assertEquals(
            listOf("Metals conduct heat", "and electricity."),
            texts("Metals conduct heat and electricity.", SegmentUnit.PHRASE),
        )
    }

    @Test
    fun phraseMode_english_keepsNumbersAndAbbreviationsTogether() {
        val source = "Pi is about 3.14 and e.g. 1,000.50 dollars is a price, not a constant."

        val segments = texts(source, SegmentUnit.PHRASE)

        for (token in listOf("3.14", "e.g.", "1,000.50")) {
            assertTrue("$token was split: $segments", segments.any { token in it })
        }
    }

    // ---- Phrase mode: Thai -----------------------------------------------------------------

    @Test
    fun phraseMode_thai_cutsAtSpacesBetweenPhrases() {
        val source = "พันธะเคมีคือแรงยึดเหนี่ยวระหว่างอะตอม ที่ทำให้อะตอมรวมกันเป็นโมเลกุลหรือสารประกอบ " +
            "อะตอมจะสร้างพันธะเพื่อให้มีการจัดเรียงอิเล็กตรอนที่เสถียร ตามกฎออกเตต"

        assertEquals(
            listOf(
                "พันธะเคมีคือแรงยึดเหนี่ยวระหว่างอะตอม",
                "ที่ทำให้อะตอมรวมกันเป็นโมเลกุลหรือสารประกอบ",
                "อะตอมจะสร้างพันธะเพื่อให้มีการจัดเรียงอิเล็กตรอนที่เสถียร",
                "ตามกฎออกเตต",
            ),
            texts(source, SegmentUnit.PHRASE),
        )
    }

    @Test
    fun phraseMode_thai_mergesShortPhrases() {
        val source = "ไอออนิก = ให้และรับอิเล็กตรอน, โคเวเลนต์ = ใช้อิเล็กตรอนร่วมกัน, โลหะ = อิเล็กตรอนเคลื่อนที่อิสระ"

        assertEquals(
            listOf(
                "ไอออนิก = ให้และรับอิเล็กตรอน,",
                "โคเวเลนต์ = ใช้อิเล็กตรอนร่วมกัน,",
                "โลหะ = อิเล็กตรอนเคลื่อนที่อิสระ",
            ),
            texts(source, SegmentUnit.PHRASE),
        )
    }

    @Test
    fun phraseMode_thai_tinyLeftoverJoinsPreviousPhrase() {
        val source = "พันธะเคมีคือแรงยึดเหนี่ยวระหว่างอะตอม ครับ"

        assertEquals(listOf(source), texts(source, SegmentUnit.PHRASE))
    }

    @Test
    fun phraseMode_thai_keepsAbbreviationsTogether() {
        val source = "ค้นพบเมื่อ พ.ศ. 2567 โดยนักวิทยาศาสตร์ไทย ที่มีปริมาณ 1,250.75 กรัม"

        val segments = texts(source, SegmentUnit.PHRASE)

        for (token in listOf("พ.ศ.", "1,250.75")) {
            assertTrue("$token was split: $segments", segments.any { token in it })
        }
    }

    @Test
    fun phraseMode_thai_cutsLongUnspacedRunAtWordBoundaries() {
        // Android always ships ICU's Thai dictionary; a minimal desktop JDK might not.
        assumeTrue("JVM has no Thai word breaking", jvmBreaksThaiWords())
        val source = "การทดลองนี้แสดงให้เห็นว่าโลหะนำไฟฟ้าได้ดีเพราะเวเลนซ์อิเล็กตรอนเคลื่อนที่ได้" +
            "อย่างอิสระทั่วทั้งก้อนโลหะคล้ายทะเลอิเล็กตรอนจึงนำความร้อนได้ดีด้วย"

        val segments = Segmenter.split(source, SegmentUnit.PHRASE)

        assertTrue("expected several pieces, got $segments", segments.size >= 2)
        assertEquals(source, segments.joinToString("") { it.text })
        for (segment in segments) {
            assertTrue(Segmenter.visibleLength(segment.text) <= Segmenter.MAX_UNBROKEN_LENGTH)
            // Never start a piece with a vowel or tone mark that belongs to the previous letter.
            val first = segment.text.codePointAt(0)
            assertTrue(
                "piece starts with a combining mark: ${segment.text}",
                Character.getType(first) != Character.NON_SPACING_MARK.toInt(),
            )
        }
    }

    @Test
    fun phraseMode_neverCrossesLineBreaks() {
        val segments = Segmenter.split("สั้น\nอีกบรรทัดหนึ่ง\nShort line", SegmentUnit.PHRASE)

        assertEquals(listOf("สั้น", "อีกบรรทัดหนึ่ง", "Short line"), segments.map { it.text })
        assertEquals(listOf(0, 1, 2), segments.map { it.line })
    }

    @Test
    fun phraseMode_cjkPunctuationBreaksWithoutSpaces() {
        val source = "今天天气很好，我们去公园散步吧。然后一起吃午饭，好不好？"

        assertEquals(
            listOf("今天天气很好，", "我们去公园散步吧。", "然后一起吃午饭，", "好不好？"),
            texts(source, SegmentUnit.PHRASE, minLength = 5),
        )
    }

    // ---- Offsets and helpers ---------------------------------------------------------------

    @Test
    fun segmentOffsetsPointBackIntoSource() {
        val source = "  พันธะเคมี (Chemical Bond)\r\n\nพันธะไอออนิก เกิดระหว่างโลหะกับอโลหะ " +
            "โลหะให้อิเล็กตรอนกลายเป็นไอออนบวก\nThe quick brown fox jumps over the lazy dog.  "

        for (unit in SegmentUnit.entries) {
            val segments = Segmenter.split(source, unit)
            assertTrue(segments.isNotEmpty())
            var previousEnd = 0
            for (segment in segments) {
                assertEquals(segment.text, source.substring(segment.start, segment.end))
                assertTrue("segments must be in order", segment.start >= previousEnd)
                previousEnd = segment.end
            }
        }
    }

    @Test
    fun indexForOffset_keepsPlaceWhenSwitchingUnits() {
        val source = "บรรทัดแรกของเอกสารตัวอย่างนี้ยาวพอสมควร\n" +
            "พันธะไอออนิก เกิดระหว่างโลหะกับอโลหะ โลหะให้อิเล็กตรอนกลายเป็นไอออนบวก"
        val lines = Segmenter.split(source, SegmentUnit.LINE)
        val phrases = Segmenter.split(source, SegmentUnit.PHRASE)

        val phraseIndex = Segmenter.indexForOffset(phrases, lines[1].start)

        assertEquals(1, phrases[phraseIndex].line)
        assertEquals(lines[1].start, phrases[phraseIndex].start)
        assertEquals(1, Segmenter.indexForOffset(lines, phrases.last().start))
        assertEquals(phrases.lastIndex, Segmenter.indexForOffset(phrases, source.length + 10))
        assertEquals(0, Segmenter.indexForOffset(emptyList(), 5))
    }

    @Test
    fun visibleLength_ignoresThaiVowelAndToneMarks() {
        assertEquals(2, Segmenter.visibleLength("ที่นี่"))
        assertEquals(2, Segmenter.visibleLength("น้ำ"))
        assertEquals(3, Segmenter.visibleLength("abc"))
        assertEquals(2, Segmenter.visibleLength("ก​ข"))
    }

    private fun jvmBreaksThaiWords(): Boolean {
        val words = BreakIterator.getWordInstance(Locale.forLanguageTag("th"))
        words.setText("ภาษาไทยง่ายนิดเดียว")
        words.first()
        return words.next() in 1 until "ภาษาไทยง่ายนิดเดียว".length
    }
}
