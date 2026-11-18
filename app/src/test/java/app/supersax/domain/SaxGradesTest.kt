package app.supersax.domain

import app.supersax.domain.drills.EarDrill
import app.supersax.domain.drills.ReadingDrill
import app.supersax.domain.drills.ScaleDegrees
import app.supersax.domain.drills.ScaleRecognition
import app.supersax.domain.grades.Award
import app.supersax.domain.grades.ExamResult
import app.supersax.domain.grades.ExamSection
import app.supersax.domain.grades.SaxGrades
import app.supersax.domain.pitch.Instrument
import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.scale.ScaleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaxGradesTest {
    @Test fun sectionsAddUpTo100() = assertEquals(100, ExamSection.entries.sumOf { it.marks })

    @Test fun everyGradeIsPlayable() {
        val alto = Instrument.ALTO_SAX
        SaxGrades.grades.forEachIndexed { i, g ->
            assertEquals(i + 1, g.number)
            assertTrue(g.scales.count { !it.arpeggio } >= 2 && g.scales.any { it.arpeggio }, "grade ${g.number} items")
            g.scales.forEach { s ->
                ScaleId.fromWritten(PitchClass.parse(s.writtenTonic), s.type, alto)
                // Some octave of the tonic fits the range on alto (written Bb3 to F6).
                val pc = PitchClass.parse(s.writtenTonic).semitone
                assertTrue((alto.writtenLowMidi..alto.writtenHighMidi).any { m -> Math.floorMod(m, 12) == pc && m + s.range <= alto.writtenHighMidi },
                    "grade ${g.number} ${s.writtenTonic} ${s.type} over ${s.range}")
            }
            assertEquals(3, SaxGrades.pickScales(g, 5).size)
            assertEquals(1, SaxGrades.pickScales(g, 5).count { it.arpeggio })
            assertTrue(g.longToneNotes.all { it in alto.writtenLowMidi..alto.writtenHighMidi }, "grade ${g.number} long tones")
            assertTrue(g.readingLevel in 1..ReadingDrill.LEVELS && g.earLevel in 1..EarDrill.LEVELS)
            assertTrue(g.degreesLevel in 1..ScaleDegrees.LEVELS && g.recognitionLevel in 1..ScaleRecognition.LEVELS)
            if (i > 0) {
                val p = SaxGrades.grades[i - 1]
                assertTrue(g.scaleCrotchet >= p.scaleCrotchet && g.arpeggioNotesPerMin >= p.arpeggioNotesPerMin)
                assertTrue(g.longToneSec > p.longToneSec && g.longToneInTune >= p.longToneInTune)
            }
        }
    }

    @Test fun awardsAndUnlocking() {
        assertEquals(Award.FAIL, SaxGrades.award(59))
        assertEquals(Award.PASS, SaxGrades.award(60))
        assertEquals(Award.MERIT, SaxGrades.award(75))
        assertEquals(Award.DISTINCTION, SaxGrades.award(87))
        fun r(g: Int, total: Int) = ExamResult(g, 0, mapOf("TONE" to total))
        assertEquals(1, SaxGrades.open(emptyList()))
        assertEquals(1, SaxGrades.open(listOf(r(1, 50))))
        assertEquals(3, SaxGrades.open(listOf(r(1, 50), r(1, 70), r(2, 90))))
        assertEquals(8, SaxGrades.open(SaxGrades.grades.map { r(it.number, 100) }))
    }

    @Test fun scoring() {
        val g = SaxGrades.grade(3)
        val c = g.scales.first { it.writtenTonic == "C" && !it.arpeggio } // two octaves, 168 notes a minute
        assertEquals(168, g.notesPerMin(c))
        assertEquals(1.0, SaxGrades.scaleScore(true, 170.0, 24, c, g, false), 1e-9)
        assertEquals(0.5, SaxGrades.scaleScore(true, 84.0, 24, c, g, false), 1e-9)
        assertEquals(0.5, SaxGrades.scaleScore(true, 170.0, 12, c, g, false), 1e-9)
        assertEquals(0.8, SaxGrades.scaleScore(true, 200.0, 24, c, g, true), 1e-9)
        assertEquals(0.0, SaxGrades.scaleScore(false, 200.0, 24, c, g, false), 1e-9)
        assertEquals(1.0, SaxGrades.toneScore(12.0, 0.9, g), 1e-9)
        assertEquals(25, SaxGrades.marks(ExamSection.SCALES, 1.0))
    }

}
