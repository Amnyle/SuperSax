package app.supersax.domain

import app.supersax.domain.library.Fraction
import app.supersax.domain.library.Score
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.notation.Smufl
import app.supersax.notation.StaffGeometry
import app.supersax.notation.StaffItem
import app.supersax.notation.StaffLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaffLayoutTest {
    private fun glyphs(items: List<StaffItem>) = items.filterIsInstance<StaffItem.Glyph>()

    @Test fun geometry() {
        assertEquals(4f, StaffGeometry.y(StaffGeometry.step(SpelledPitch.parse("E4")))) // bottom line
        assertEquals(0f, StaffGeometry.y(StaffGeometry.step(SpelledPitch.parse("F5")))) // top line
        assertEquals(listOf(-2), StaffGeometry.ledgerSteps(StaffGeometry.step(SpelledPitch.parse("C4"))))
        assertEquals(listOf(-2), StaffGeometry.ledgerSteps(StaffGeometry.step(SpelledPitch.parse("B3"))))
        assertEquals(listOf(-2, -4), StaffGeometry.ledgerSteps(StaffGeometry.step(SpelledPitch.parse("A3"))))
        assertEquals(listOf(10, 12, 14), StaffGeometry.ledgerSteps(StaffGeometry.step(SpelledPitch.parse("F#6"))))
        assertEquals(SpelledPitch.parse("G4"), StaffGeometry.pitchAt(2))
    }

    @Test fun accidentalsFollowKeyAndMeasureMemory() {
        // Written A major (3 sharps): F#, C#, G# need no sign; G natural needs one; the second G natural in the bar does not.
        val score = Score(3, 4, 4, 80, listOf(
            Score.parseMeasure("A4:1/4 F#5:1/4 G4:1/4 G4:1/4"),
            Score.parseMeasure("G4:1/2 Bb4:1/2"),
        ))
        val accs = glyphs(StaffLayout.layout(score, 200f).single().items).filter { it.noteIndex >= 0 && it.text in listOf(Smufl.SHARP, Smufl.FLAT, Smufl.NATURAL) }
        assertEquals(listOf(Smufl.NATURAL to 2, Smufl.NATURAL to 4, Smufl.FLAT to 5), accs.map { it.text to it.noteIndex })
        // Key signature: three sharps drawn before the notes.
        assertEquals(3, glyphs(StaffLayout.layout(score, 200f).single().items).count { it.noteIndex == -1 && it.text == Smufl.SHARP })
    }

    @Test fun durationsDotsTiesAndWrapping() {
        assertEquals(listOf(Fraction(1, 2), Fraction(1, 8)), StaffLayout.decompose(Fraction(5, 8)))
        assertEquals(listOf(Fraction(3, 8)), StaffLayout.decompose(Fraction(3, 8)))
        val m = "C5:3/8 D5:1/8 E5:1/4~ E5:1/4"
        val score = Score(0, 4, 4, 80, List(12) { Score.parseMeasure(m) })
        val systems = StaffLayout.layout(score, 60f)
        assertTrue(systems.size > 1, "wraps into several systems")
        val all = systems.flatMap { it.items }
        assertEquals(12, glyphs(all).count { it.text == Smufl.DOT })
        assertEquals(12, all.count { it is StaffItem.Tie })
        assertEquals(12, glyphs(all).count { it.text == Smufl.FLAG_8TH_DOWN })
        // Time signature only on the first system.
        assertEquals(1, glyphs(systems[0].items).count { it.text == Smufl.timeDigit('4') + "" && it.y == 1f })
        assertEquals(0, glyphs(systems[1].items).count { it.y == 1f && it.text == Smufl.timeDigit('4') })
    }

    @Test fun ledgerLinesAndStemDirection() {
        val score = Score(0, 4, 4, 80, listOf(Score.parseMeasure("Bb3:1/4 A5:1/4 r:1/4 B4:1/4")))
        val items = StaffLayout.layout(score, 100f).single().items
        val ledgers = items.filterIsInstance<StaffItem.Line>().filter { it.y1 == it.y2 && it.noteIndex >= 0 }
        assertEquals(2, ledgers.size) // one below for B♭3, one above for A5
        val stems = items.filterIsInstance<StaffItem.Line>().filter { it.x1 == it.x2 && it.noteIndex >= 0 }
        assertTrue(stems[0].y2 < stems[0].y1) // low note: stem up
        assertTrue(stems[1].y2 > stems[1].y1) // high note: stem down
        assertTrue(glyphs(items).any { it.text == Smufl.REST_QUARTER })
    }
}
