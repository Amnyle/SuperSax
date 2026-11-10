package app.saxpractice.domain

import app.saxpractice.domain.pitch.Instrument
import app.saxpractice.domain.pitch.KeySignature
import app.saxpractice.domain.pitch.Letter
import app.saxpractice.domain.pitch.NoteNaming
import app.saxpractice.domain.pitch.PitchClass
import app.saxpractice.domain.pitch.SpelledPitch
import app.saxpractice.domain.pitch.Tuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranspositionTest {
    private val alto = Instrument.ALTO_SAX

    @Test fun groundTruth() {
        assertEquals(60, alto.writtenToConcert(SpelledPitch.parse("A4")).midi)
        assertEquals("C4", alto.writtenToConcert(SpelledPitch.parse("A4")).toString())
        assertEquals("Eb4", alto.writtenToConcert(SpelledPitch.parse("C5")).toString())
        assertEquals(63, alto.writtenToConcert(SpelledPitch.parse("C5")).midi)
    }

    @Test fun rangeBoundaries() {
        assertEquals(58, alto.writtenLowMidi); assertEquals(90, alto.writtenHighMidi)
        assertEquals(49, alto.concertLowMidi); assertEquals(81, alto.concertHighMidi)
        assertEquals("Db3", alto.writtenToConcert(SpelledPitch.parse("Bb3")).toString())
        assertEquals("A5", alto.writtenToConcert(SpelledPitch.parse("F#6")).toString())
    }

    /** Written chromatic spelling -> expected concert spelling (a major sixth below). */
    private val expected = mapOf(
        "C" to "Eb", "C#" to "E", "D" to "F", "Eb" to "Gb", "E" to "G", "F" to "Ab",
        "F#" to "A", "G" to "Bb", "G#" to "B", "A" to "C", "Bb" to "Db", "B" to "D",
    )

    @Test fun everyWrittenNoteInRange() {
        for (w in alto.writtenLowMidi..alto.writtenHighMidi) {
            val written = SpelledPitch.fromMidi(w)
            val concert = alto.writtenToConcert(written)
            assertEquals(w - 9, concert.midi, "midi for $written")
            assertEquals(expected[written.pitchClass.ascii()], concert.pitchClass.ascii(), "spelling for $written")
            assertEquals(written, alto.concertToWritten(concert), "round trip for $written")
            // Five letters apart, always.
            assertEquals(5, written.diatonicIndex - concert.diatonicIndex)
        }
    }

    @Test fun keySignatures() {
        assertEquals(3, alto.writtenKey(KeySignature(0)).fifths) // concert C -> written A
        assertEquals(0, alto.writtenKey(KeySignature(-3)).fifths) // concert Eb -> written C
        assertEquals(1, alto.writtenKey(KeySignature(-2)).fifths) // concert Bb -> written G
        assertEquals(2, alto.writtenKey(KeySignature(-1)).fifths) // concert F -> written D
        assertEquals(-1, alto.writtenKey(KeySignature(-4)).fifths) // concert Ab -> written F
        assertEquals(6, alto.writtenKey(KeySignature(3)).fifths) // concert A -> written F#
        assertEquals(-5, alto.writtenKey(KeySignature(4)).fifths) // concert E -> written Db (not C#)
        assertEquals(PitchClass(Letter.A), KeySignature(3).majorTonic)
        assertEquals(PitchClass(Letter.E, -1), KeySignature(-3).majorTonic)
        assertEquals(PitchClass(Letter.C), KeySignature(-3).minorTonic)
        assertEquals(listOf("F#", "C#", "G#"), KeySignature(3).accidentals.map { it.ascii() })
        assertEquals(listOf("Bb", "Eb", "Ab"), KeySignature(-3).accidentals.map { it.ascii() })
    }

    @Test fun enharmonicRespellingInSharpKeys() {
        // Concert E major written in Db: concert G# is written F, not E#.
        assertEquals("F5", alto.concertToWritten(SpelledPitch.parse("G#4"), concertFifths = 4).toString())
        assertEquals("E#5", alto.concertToWritten(SpelledPitch.parse("G#4"), concertFifths = 0).toString())
    }

    @Test fun naming() {
        assertEquals("E♭4", NoteNaming.LETTERS.format(SpelledPitch.parse("Eb4")))
        assertEquals("Mi♭4", NoteNaming.SOLFEGE.format(SpelledPitch.parse("Eb4")))
        assertEquals("Fa♯", NoteNaming.SOLFEGE.format(PitchClass(Letter.F, 1)))
        assertEquals(PitchClass(Letter.D, 1), NoteNaming.parseAny("Ré#"))
        assertEquals(PitchClass(Letter.B, -1), NoteNaming.parseAny("sib"))
        assertEquals(PitchClass(Letter.B, -1), NoteNaming.parseAny("Bb"))
        assertEquals(PitchClass(Letter.G), NoteNaming.parseAny("sol"))
        assertNull(NoteNaming.parseAny("H"))
    }

    @Test fun cents() {
        assertEquals(440.0, Tuning.midiToFrequency(69), 1e-9)
        assertEquals(261.6256, Tuning.midiToFrequency(60), 1e-3)
        assertEquals(100.0, Tuning.cents(Tuning.midiToFrequency(70), Tuning.midiToFrequency(69)), 1e-9)
        assertEquals(442.0, Tuning.midiToFrequency(69, 442.0), 1e-9)
    }
}
