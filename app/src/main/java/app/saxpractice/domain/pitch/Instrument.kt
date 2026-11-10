package app.saxpractice.domain.pitch

import kotlinx.serialization.Serializable

/**
 * Transposing instrument. Only the alto saxophone exists for now; the value is stored as a setting
 * so a tenor could be added later without touching stored data (everything is stored in concert pitch).
 *
 * @property writtenAboveConcert interval from concert (sounding) pitch up to written pitch.
 * @property writtenLow lowest written note of the standard range.
 * @property writtenHigh highest written note of the standard range (altissimo starts above).
 */
@Serializable
enum class Instrument(
    val writtenAboveConcert: Interval,
    val writtenLowMidi: Int,
    val writtenHighMidi: Int,
) {
    /** E♭ alto: sounds a major sixth below written. Written B♭3..F♯6. */
    ALTO_SAX(Interval.MAJOR_SIXTH, 58, 90);

    val concertLowMidi: Int get() = writtenLowMidi - writtenAboveConcert.semitones
    val concertHighMidi: Int get() = writtenHighMidi - writtenAboveConcert.semitones

    /** Key-signature shift from concert to written (alto: +3 sharps). */
    val fifthsShift: Int get() = fifthsOfInterval(writtenAboveConcert)

    /**
     * Interval to use for a piece or key in concert key [concertFifths]: the nominal interval, or its
     * enharmonic twin when the written key would need more than six sharps (concert E major is
     * written D♭ major rather than C♯ major).
     */
    fun writtenInterval(concertFifths: Int): Interval {
        val w = concertFifths + fifthsShift
        return when {
            w > 6 -> Interval(writtenAboveConcert.diatonicSteps + 1, writtenAboveConcert.semitones)
            w < -6 -> Interval(writtenAboveConcert.diatonicSteps - 1, writtenAboveConcert.semitones)
            else -> writtenAboveConcert
        }
    }

    fun concertToWritten(p: SpelledPitch, concertFifths: Int = 0): SpelledPitch = p.transpose(writtenInterval(concertFifths))
    fun writtenToConcert(p: SpelledPitch): SpelledPitch = p.transpose(-writtenAboveConcert)
    fun concertToWrittenMidi(concertMidi: Int): Int = concertMidi + writtenAboveConcert.semitones
    fun writtenToConcertMidi(writtenMidi: Int): Int = writtenMidi - writtenAboveConcert.semitones

    fun writtenKey(concert: KeySignature): KeySignature =
        KeySignature(concert.fifths + fifthsOfInterval(writtenInterval(concert.fifths)))

    companion object {
        /** Number of fifths an interval moves a key: M6 = +3, dim7 = -9. */
        fun fifthsOfInterval(i: Interval): Int {
            // Transposing C by the interval and reading the key signature of the resulting major key.
            val pc = PitchClass(Letter.C).transpose(i)
            return KeySignature.majorFifths(pc)
        }
    }
}

/** Alto register boundaries in written MIDI, used by onboarding, rank (Range, Sound) and drills. */
object AltoRegisters {
    const val LOW_BB = 58
    const val LOW_D = 62
    const val MIDDLE_D = 74 // written D5, first octave-key note
    const val PALM_D = 86 // written D6
    const val HIGH_FSHARP = 90
    const val ALTISSIMO_G = 91

    enum class Register { LOW, MIDDLE, HIGH, ALTISSIMO }

    /** Register of a written MIDI note: low B♭3..C♯5, middle D5..C♯6, high D6..F♯6, altissimo above. */
    fun registerOf(writtenMidi: Int): Register = when {
        writtenMidi < MIDDLE_D -> Register.LOW
        writtenMidi < PALM_D -> Register.MIDDLE
        writtenMidi <= HIGH_FSHARP -> Register.HIGH
        else -> Register.ALTISSIMO
    }
}

/** A key signature as a count of fifths: positive sharps, negative flats (-7..7). */
@Serializable
data class KeySignature(val fifths: Int) {
    /** Major tonic of this signature (C for 0, G for 1, F for -1...). */
    val majorTonic: PitchClass get() = SHARP_ORDER_TONIC(fifths)
    val minorTonic: PitchClass get() = majorTonic.transpose(Interval(-2, -3))

    /** The altered letters, in order of appearance on the staff. */
    val accidentals: List<PitchClass>
        get() = if (fifths >= 0) SHARPS.take(fifths).map { PitchClass(it, 1) }
        else FLATS.take(-fifths).map { PitchClass(it, -1) }

    /** Alteration this key signature applies to [letter]. */
    fun alterFor(letter: Letter): Int = accidentals.firstOrNull { it.letter == letter }?.alter ?: 0

    companion object {
        val SHARPS = listOf(Letter.F, Letter.C, Letter.G, Letter.D, Letter.A, Letter.E, Letter.B)
        val FLATS = SHARPS.reversed()
        private val NATURAL_MAJOR_FIFTHS = mapOf(
            Letter.F to -1, Letter.C to 0, Letter.G to 1, Letter.D to 2, Letter.A to 3, Letter.E to 4, Letter.B to 5,
        )

        /** Fifths of the major key on [tonic] (E♭ → -3, F♯ → 6). */
        fun majorFifths(tonic: PitchClass): Int = NATURAL_MAJOR_FIFTHS.getValue(tonic.letter) + 7 * tonic.alter

        private fun SHARP_ORDER_TONIC(fifths: Int): PitchClass {
            var pc = PitchClass(Letter.C)
            val step = if (fifths >= 0) Interval(4, 7) else Interval(3, 5)
            repeat(kotlin.math.abs(fifths)) { pc = pc.transpose(step) }
            return pc
        }
    }
}
