package app.saxpractice.domain.scale

import app.saxpractice.domain.pitch.Instrument
import app.saxpractice.domain.pitch.Interval
import app.saxpractice.domain.pitch.KeySignature
import app.saxpractice.domain.pitch.PitchClass
import app.saxpractice.domain.pitch.SpelledPitch
import kotlinx.serialization.Serializable

/**
 * Scale (or arpeggio) definitions as interval patterns. Note sequences are always generated from these,
 * never typed by hand.
 *
 * @property steps diatonic steps from the tonic for each degree.
 * @property semitones semitones from the tonic for each degree.
 * @property fifthsOffset key-signature offset from the major key on the same tonic (minor = -3).
 */
@Serializable
enum class ScaleType(val steps: List<Int>, val semitones: List<Int>, val fifthsOffset: Int, val family: Family) {
    MAJOR(listOf(0, 1, 2, 3, 4, 5, 6), listOf(0, 2, 4, 5, 7, 9, 11), 0, Family.MAJOR),
    NATURAL_MINOR(listOf(0, 1, 2, 3, 4, 5, 6), listOf(0, 2, 3, 5, 7, 8, 10), -3, Family.MINOR),
    DORIAN(listOf(0, 1, 2, 3, 4, 5, 6), listOf(0, 2, 3, 5, 7, 9, 10), -2, Family.MODE),
    PHRYGIAN(listOf(0, 1, 2, 3, 4, 5, 6), listOf(0, 1, 3, 5, 7, 8, 10), -4, Family.MODE),
    LYDIAN(listOf(0, 1, 2, 3, 4, 5, 6), listOf(0, 2, 4, 6, 7, 9, 11), 1, Family.MODE),
    MIXOLYDIAN(listOf(0, 1, 2, 3, 4, 5, 6), listOf(0, 2, 4, 5, 7, 9, 10), -1, Family.MODE),
    LOCRIAN(listOf(0, 1, 2, 3, 4, 5, 6), listOf(0, 1, 3, 5, 6, 8, 10), -5, Family.MODE),
    MAJOR_ARPEGGIO(listOf(0, 2, 4), listOf(0, 4, 7), 0, Family.ARPEGGIO),
    MINOR_ARPEGGIO(listOf(0, 2, 4), listOf(0, 3, 7), -3, Family.ARPEGGIO);

    enum class Family { MAJOR, MINOR, MODE, ARPEGGIO }

    /** Whole (W) and half (H) step pattern, e.g. "WWHWWWH" for major. Null for arpeggios. */
    val stepPattern: String?
        get() = if (family == Family.ARPEGGIO) null else
            (semitones + 12).zipWithNext { a, b -> if (b - a == 2) 'W' else if (b - a == 1) 'H' else '?' }.joinToString("")
}

/**
 * A scale identity: a concert-pitch tonic and a type. Stored in concert pitch (the storage standard);
 * the UI shows the written name via [writtenTonic].
 */
@Serializable
data class ScaleId(val tonic: PitchClass, val type: ScaleType) {
    /** Stable string id, e.g. "MAJOR:Eb" (concert). */
    val key: String get() = "${type.name}:${tonic.ascii()}"

    /** Concert key signature of this scale. */
    val keySignature: KeySignature get() = KeySignature(KeySignature.majorFifths(tonic) + type.fifthsOffset)

    /** Tonic as the alto player reads it. */
    fun writtenTonic(instrument: Instrument = Instrument.ALTO_SAX): PitchClass =
        tonic.transpose(instrument.writtenInterval(keySignature.fifths))

    fun writtenKeySignature(instrument: Instrument = Instrument.ALTO_SAX): KeySignature = instrument.writtenKey(keySignature)

    companion object {
        fun parse(key: String): ScaleId {
            val (t, pc) = key.split(":")
            return ScaleId(PitchClass.parse(pc), ScaleType.valueOf(t))
        }

        /** Builds a scale id from the written tonic the player sees (written C major → concert E♭ major). */
        fun fromWritten(writtenTonic: PitchClass, type: ScaleType, instrument: Instrument = Instrument.ALTO_SAX): ScaleId {
            val candidate = writtenTonic.transpose(-instrument.writtenAboveConcert)
            val normalised = normaliseConcertTonic(candidate, type)
            return ScaleId(normalised, type)
        }

        /** Keeps concert keys between 6 flats and 5 sharps (G♭ rather than F♯), the set used by [all]. */
        fun normaliseConcertTonic(tonic: PitchClass, type: ScaleType): PitchClass {
            val f = KeySignature.majorFifths(tonic) + type.fifthsOffset
            return when {
                f > 5 -> tonic.transpose(Interval(1, 0))
                f < -6 -> tonic.transpose(Interval(-1, 0))
                else -> tonic
            }
        }

        /** The 12 tonics of a scale type, concert spelling, key signatures from 6 flats to 5 sharps. */
        fun all(type: ScaleType): List<ScaleId> = (-6..5).map { f ->
            val tonic = modeTonic(KeySignature(f), type)
            ScaleId(tonic, type).also { check(it.keySignature.fifths == f) { "bad mode tonic for $f" } }
        }

        private fun modeTonic(sig: KeySignature, type: ScaleType): PitchClass {
            val majorTonic = sig.majorTonic
            // Degree of the major scale that starts the mode: major 0, dorian 1, phrygian 2, lydian 3, mixo 4, minor 5, locrian 6.
            val degree = when (type) {
                ScaleType.MAJOR, ScaleType.MAJOR_ARPEGGIO -> 0
                ScaleType.DORIAN -> 1
                ScaleType.PHRYGIAN -> 2
                ScaleType.LYDIAN -> 3
                ScaleType.MIXOLYDIAN -> 4
                ScaleType.NATURAL_MINOR, ScaleType.MINOR_ARPEGGIO -> 5
                ScaleType.LOCRIAN -> 6
            }
            val p = SpelledPitch(majorTonic.letter, majorTonic.alter, 4)
            return p.transpose(Interval(degree, ScaleType.MAJOR.semitones[degree])).pitchClass
        }
    }
}

/** Generates spelled note sequences for scales and arpeggios. */
object ScaleGenerator {
    /** One octave, ascending, tonic to tonic, starting at [tonicOctave]. */
    fun oneOctave(scale: ScaleId, tonicOctave: Int = 4): List<SpelledPitch> {
        val root = SpelledPitch(scale.tonic.letter, scale.tonic.alter, tonicOctave)
        return scale.type.steps.indices.map { i ->
            root.transpose(Interval(scale.type.steps[i], scale.type.semitones[i]))
        } + root.transpose(Interval.OCTAVE)
    }

    /**
     * All notes of the scale between [lowMidi] and [highMidi] (concert, inclusive), ascending.
     * Used for "full range" scale runs on the player's instrument range.
     */
    fun inRange(scale: ScaleId, lowMidi: Int, highMidi: Int): List<SpelledPitch> {
        val out = mutableListOf<SpelledPitch>()
        for (octave in -1..9) {
            val root = SpelledPitch(scale.tonic.letter, scale.tonic.alter, octave)
            scale.type.steps.indices.forEach { i ->
                val p = root.transpose(Interval(scale.type.steps[i], scale.type.semitones[i]))
                if (p.midi in lowMidi..highMidi) out += p
            }
        }
        return out.sortedBy { it.midi }.distinctBy { it.midi }
    }

    /** Full range up and down (the top note played once), as counted by the Technique pillar. */
    fun fullRangeUpDown(scale: ScaleId, lowMidi: Int, highMidi: Int): List<SpelledPitch> {
        val up = inRange(scale, lowMidi, highMidi)
        return up + up.dropLast(1).reversed()
    }

    /** Alto full range in concert pitch for [instrument]. */
    fun fullRangeOnInstrument(scale: ScaleId, instrument: Instrument = Instrument.ALTO_SAX) =
        fullRangeUpDown(scale, instrument.concertLowMidi, instrument.concertHighMidi)

    /** Builds a scale from a root and a W/H pattern, as in the "build the scale" drill. */
    fun fromPattern(root: SpelledPitch, pattern: String): List<SpelledPitch> {
        var current = root
        val out = mutableListOf(root)
        pattern.forEach { c ->
            current = current.transpose(Interval(1, if (c == 'W') 2 else 1))
            out += current
        }
        return out
    }
}
