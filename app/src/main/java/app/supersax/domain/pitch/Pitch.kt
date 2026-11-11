package app.supersax.domain.pitch

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.log2
import kotlin.math.pow

/** The seven note letters with their diatonic index and natural semitone offset from C. */
enum class Letter(val index: Int, val semitone: Int) {
    C(0, 0), D(1, 2), E(2, 4), F(3, 5), G(4, 7), A(5, 9), B(6, 11);

    companion object {
        fun ofIndex(i: Int): Letter = entries[Math.floorMod(i, 7)]
    }
}

/**
 * An interval measured both in diatonic steps (letter distance) and semitones,
 * so that transposing keeps correct spelling (C up a major sixth is A, never B♭♭).
 */
data class Interval(val diatonicSteps: Int, val semitones: Int) {
    operator fun unaryMinus() = Interval(-diatonicSteps, -semitones)

    companion object {
        val UNISON = Interval(0, 0)
        val MAJOR_SIXTH = Interval(5, 9)
        /** Enharmonic twin of the major sixth, used to avoid keys with more than six sharps. */
        val DIMINISHED_SEVENTH = Interval(6, 9)
        val OCTAVE = Interval(7, 12)
    }
}

/** A pitch class with spelling: a letter and an alteration (-2..+2). */
@Serializable
data class PitchClass(val letter: Letter, val alter: Int = 0) {
    val semitone: Int get() = Math.floorMod(letter.semitone + alter, 12)

    fun transpose(interval: Interval): PitchClass =
        SpelledPitch(letter, alter, 4).transpose(interval).pitchClass

    /** ASCII spelling such as "Eb" or "F#". Used as a stable id in data files. */
    fun ascii(): String = letter.name + accidentalAscii(alter)

    companion object {
        fun parse(s: String): PitchClass {
            val letter = Letter.valueOf(s.substring(0, 1).uppercase())
            return PitchClass(letter, parseAccidental(s.substring(1)))
        }
    }
}

/**
 * A spelled pitch: letter, alteration and octave (scientific pitch notation, C4 = middle C = MIDI 60).
 *
 * Concert pitch is the storage standard everywhere in the app; written alto pitch is derived with
 * [Instrument.concertToWritten].
 */
@Serializable(with = SpelledPitchSerializer::class)
data class SpelledPitch(val letter: Letter, val alter: Int, val octave: Int) : Comparable<SpelledPitch> {
    /** MIDI number, A4 = 69, C4 = 60. */
    val midi: Int get() = (octave + 1) * 12 + letter.semitone + alter

    /** Absolute diatonic index (C0 = 0); one unit is one staff step (line to space). */
    val diatonicIndex: Int get() = octave * 7 + letter.index

    val pitchClass: PitchClass get() = PitchClass(letter, alter)

    /** Transposes with correct spelling: the letter moves by the diatonic steps, the alteration fixes the semitones. */
    fun transpose(interval: Interval): SpelledPitch {
        val newDiatonic = diatonicIndex + interval.diatonicSteps
        val newLetter = Letter.ofIndex(newDiatonic)
        val newOctave = Math.floorDiv(newDiatonic, 7)
        val naturalMidi = (newOctave + 1) * 12 + newLetter.semitone
        val newAlter = midi + interval.semitones - naturalMidi
        return SpelledPitch(newLetter, newAlter, newOctave)
    }

    /** Same sounding pitch, different spelling one letter away (G♯ ↔ A♭). */
    fun respell(letterShift: Int): SpelledPitch = transpose(Interval(letterShift, 0))

    override fun compareTo(other: SpelledPitch): Int =
        compareValuesBy(this, other, { it.midi }, { it.diatonicIndex })

    /** ASCII form such as "Eb4", "F#5", "Bbb3", "C##4". */
    override fun toString(): String = letter.name + accidentalAscii(alter) + octave

    companion object {
        private val regex = Regex("^([A-Ga-g])(bb|b|##|#|x|n)?(-?\\d+)$")

        fun parse(s: String): SpelledPitch {
            val m = regex.matchEntire(s.trim()) ?: throw IllegalArgumentException("Bad pitch: $s")
            val letter = Letter.valueOf(m.groupValues[1].uppercase())
            return SpelledPitch(letter, parseAccidental(m.groupValues[2]), m.groupValues[3].toInt())
        }

        /** Default chromatic spelling used for detected pitches: C C♯ D E♭ E F F♯ G G♯ A B♭ B. */
        fun fromMidi(midi: Int): SpelledPitch {
            val pc = Math.floorMod(midi, 12)
            val octave = Math.floorDiv(midi, 12) - 1
            val (letter, alter) = DEFAULT_SPELLING[pc]
            return SpelledPitch(letter, alter, octave)
        }

        private val DEFAULT_SPELLING = listOf(
            Letter.C to 0, Letter.C to 1, Letter.D to 0, Letter.E to -1, Letter.E to 0, Letter.F to 0,
            Letter.F to 1, Letter.G to 0, Letter.G to 1, Letter.A to 0, Letter.B to -1, Letter.B to 0,
        )
    }
}

internal fun accidentalAscii(alter: Int): String = when (alter) {
    0 -> ""
    1 -> "#"; 2 -> "##"; -1 -> "b"; -2 -> "bb"
    else -> if (alter > 0) "#".repeat(alter) else "b".repeat(-alter)
}

internal fun parseAccidental(s: String): Int = when (s) {
    "", "n" -> 0
    "#" -> 1; "##", "x" -> 2; "b" -> -1; "bb" -> -2
    else -> throw IllegalArgumentException("Bad accidental: $s")
}

object SpelledPitchSerializer : KSerializer<SpelledPitch> {
    override val descriptor = PrimitiveSerialDescriptor("SpelledPitch", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: SpelledPitch) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): SpelledPitch = SpelledPitch.parse(decoder.decodeString())
}

/** Frequency and cents helpers. Reference pitch A4 defaults to 440 Hz. */
object Tuning {
    const val DEFAULT_A4 = 440.0

    fun midiToFrequency(midi: Double, a4: Double = DEFAULT_A4): Double = a4 * 2.0.pow((midi - 69.0) / 12.0)
    fun midiToFrequency(midi: Int, a4: Double = DEFAULT_A4): Double = midiToFrequency(midi.toDouble(), a4)

    /** Fractional MIDI number of a frequency. */
    fun frequencyToMidi(f: Double, a4: Double = DEFAULT_A4): Double = 69.0 + 12.0 * log2(f / a4)

    /** Cents between [f] and [fRef]: `1200 * log2(f / fRef)`. */
    fun cents(f: Double, fRef: Double): Double = 1200.0 * log2(f / fRef)
}
