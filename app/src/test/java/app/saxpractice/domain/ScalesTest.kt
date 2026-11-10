package app.saxpractice.domain

import app.saxpractice.domain.pitch.Instrument
import app.saxpractice.domain.pitch.Letter
import app.saxpractice.domain.pitch.PitchClass
import app.saxpractice.domain.pitch.SpelledPitch
import app.saxpractice.domain.scale.ScaleGenerator
import app.saxpractice.domain.scale.ScaleId
import app.saxpractice.domain.scale.ScaleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScalesTest {
    @Test fun twelveKeysPerTypeWithCorrectSignatures() {
        ScaleType.entries.forEach { type ->
            val all = ScaleId.all(type)
            assertEquals(12, all.size, "$type")
            assertEquals(12, all.map { it.tonic.semitone }.toSet().size, "$type covers 12 pitch classes")
            all.forEach { id ->
                val notes = ScaleGenerator.oneOctave(id)
                // Each letter appears exactly once for 7-note scales.
                if (type.steps.size == 7) assertEquals(7, notes.dropLast(1).map { it.letter }.toSet().size, "$id letters")
                // Intervals match the pattern.
                assertEquals(type.semitones + 12, notes.map { it.midi - notes[0].midi }, "$id semitones")
                // Accidentals in the notes agree with the key signature.
                val sig = id.keySignature
                notes.forEach { n -> assertEquals(sig.alterFor(n.letter), n.alter, "$id ${n}") }
            }
        }
    }

    @Test fun knownScales() {
        val eb = ScaleId(PitchClass(Letter.E, -1), ScaleType.MAJOR)
        assertEquals("Eb4 F4 G4 Ab4 Bb4 C5 D5 Eb5", ScaleGenerator.oneOctave(eb).joinToString(" "))
        val fsMinor = ScaleId(PitchClass(Letter.F, 1), ScaleType.NATURAL_MINOR)
        assertEquals("F#4 G#4 A4 B4 C#5 D5 E5 F#5", ScaleGenerator.oneOctave(fsMinor).joinToString(" "))
        val dDorian = ScaleId(PitchClass(Letter.D), ScaleType.DORIAN)
        assertEquals("D4 E4 F4 G4 A4 B4 C5 D5", ScaleGenerator.oneOctave(dDorian).joinToString(" "))
        val gMixo = ScaleId(PitchClass(Letter.G), ScaleType.MIXOLYDIAN)
        assertEquals("G4 A4 B4 C5 D5 E5 F5 G5", ScaleGenerator.oneOctave(gMixo).joinToString(" "))
        val fLyd = ScaleId(PitchClass(Letter.F), ScaleType.LYDIAN)
        assertEquals("F4 G4 A4 B4 C5 D5 E5 F5", ScaleGenerator.oneOctave(fLyd).joinToString(" "))
        val ePhr = ScaleId(PitchClass(Letter.E), ScaleType.PHRYGIAN)
        assertEquals("E4 F4 G4 A4 B4 C5 D5 E5", ScaleGenerator.oneOctave(ePhr).joinToString(" "))
        val bLoc = ScaleId(PitchClass(Letter.B), ScaleType.LOCRIAN)
        assertEquals("B4 C5 D5 E5 F5 G5 A5 B5", ScaleGenerator.oneOctave(bLoc).joinToString(" "))
        val abArp = ScaleId(PitchClass(Letter.A, -1), ScaleType.MAJOR_ARPEGGIO)
        assertEquals("Ab4 C5 Eb5 Ab5", ScaleGenerator.oneOctave(abArp).joinToString(" "))
        val cMinArp = ScaleId(PitchClass(Letter.C), ScaleType.MINOR_ARPEGGIO)
        assertEquals("C4 Eb4 G4 C5", ScaleGenerator.oneOctave(cMinArp).joinToString(" "))
        assertEquals("WWHWWWH", ScaleType.MAJOR.stepPattern)
        assertEquals("WHWWHWW", ScaleType.NATURAL_MINOR.stepPattern)
    }

    @Test fun writtenNames() {
        // Concert Eb major is written C major for alto.
        val eb = ScaleId(PitchClass(Letter.E, -1), ScaleType.MAJOR)
        assertEquals(PitchClass(Letter.C), eb.writtenTonic())
        assertEquals(0, eb.writtenKeySignature().fifths)
        assertEquals(eb, ScaleId.fromWritten(PitchClass(Letter.C), ScaleType.MAJOR))
        // The 12 written majors are all sensible keys (at most 6 accidentals).
        val written = ScaleId.all(ScaleType.MAJOR).map { it.writtenTonic().ascii() }.toSet()
        assertEquals(setOf("Eb", "Bb", "F", "C", "G", "D", "A", "E", "B", "F#", "Db", "Ab"), written)
        ScaleId.all(ScaleType.MAJOR).forEach { assertEquals(it, ScaleId.fromWritten(it.writtenTonic(), ScaleType.MAJOR)) }
        ScaleId.all(ScaleType.NATURAL_MINOR).forEach { assertEquals(it, ScaleId.fromWritten(it.writtenTonic(), ScaleType.NATURAL_MINOR)) }
        assertEquals(ScaleId.parse("MAJOR:Eb"), eb)
        assertEquals("NATURAL_MINOR:F#", ScaleId(PitchClass(Letter.F, 1), ScaleType.NATURAL_MINOR).key)
    }

    @Test fun fullRangeUpAndDown() {
        val alto = Instrument.ALTO_SAX
        // Written C major = concert Eb major, full alto range.
        val eb = ScaleId.parse("MAJOR:Eb")
        val run = ScaleGenerator.fullRangeOnInstrument(eb)
        val up = ScaleGenerator.inRange(eb, alto.concertLowMidi, alto.concertHighMidi)
        assertTrue(up.all { it.midi in alto.concertLowMidi..alto.concertHighMidi })
        assertEquals("D3", up.first().toString()) // written B3, lowest scale note on the horn
        assertEquals("Ab5", up.last().toString()) // written F6
        assertEquals(up.size * 2 - 1, run.size)
        assertEquals(up.first(), run.last())
    }

    @Test fun buildFromPattern() {
        val built = ScaleGenerator.fromPattern(SpelledPitch.parse("D4"), "WWHWWWH")
        assertEquals("D4 E4 F#4 G4 A4 B4 C#5 D5", built.joinToString(" "))
    }
}
