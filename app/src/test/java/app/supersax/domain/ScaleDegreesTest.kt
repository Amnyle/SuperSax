package app.supersax.domain

import app.supersax.domain.drills.DailyMix
import app.supersax.domain.drills.ScaleDegrees
import app.supersax.domain.common.DrillKind
import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.scale.ScaleId
import app.supersax.domain.scale.ScaleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScaleDegreesTest {
    @Test fun degreesAreSpelledAsInTheScale() {
        val d = ScaleId(PitchClass.parse("D"), ScaleType.MAJOR)
        assertEquals(PitchClass.parse("A"), ScaleDegrees.Question(ScaleDegrees.Kind.NOTE_OF_DEGREE, d, 5).note)
        val third = ScaleDegrees.Question(ScaleDegrees.Kind.NOTE_OF_DEGREE, d, 3)
        assertTrue(ScaleDegrees.checkNote(third, PitchClass.parse("F#")))
        assertFalse(ScaleDegrees.checkNote(third, PitchClass.parse("Gb")))
        val f = ScaleId(PitchClass.parse("F"), ScaleType.MAJOR)
        assertEquals(PitchClass.parse("Bb"), ScaleDegrees.Question(ScaleDegrees.Kind.NOTE_OF_DEGREE, f, 4).note)
        val a = ScaleId(PitchClass.parse("A"), ScaleType.NATURAL_MINOR)
        assertEquals(PitchClass.parse("C"), ScaleDegrees.Question(ScaleDegrees.Kind.NOTE_OF_DEGREE, a, 3).note)
        assertTrue(ScaleDegrees.checkDegree(ScaleDegrees.Question(ScaleDegrees.Kind.DEGREE_OF_NOTE, a, 7), 7))
    }

    @Test fun levelsWidenAndReverseComesLater() {
        val one = ScaleDegrees.round(1, 200, 1)
        assertTrue(one.all { it.kind == ScaleDegrees.Kind.NOTE_OF_DEGREE && it.scale.type == ScaleType.MAJOR })
        assertTrue(one.all { it.scale.keySignature.fifths in -1..1 })
        assertTrue(one.all { it.degree in 2..7 })
        val six = ScaleDegrees.round(6, 400, 2)
        assertTrue(six.any { it.kind == ScaleDegrees.Kind.DEGREE_OF_NOTE })
        assertTrue(six.any { it.scale.type == ScaleType.NATURAL_MINOR })
        assertTrue(six.zipWithNext().none { (x, y) -> x.scale == y.scale && x.degree == y.degree })
    }

    @Test fun dailyMixHasScaleDegrees() {
        assertEquals(2, DailyMix.counts(false, emptyMap())[DrillKind.SCALE_DEGREES])
    }
}
