package app.saxpractice.domain.audio

import app.saxpractice.domain.pitch.Instrument
import app.saxpractice.domain.pitch.Tuning
import kotlin.math.abs

/**
 * Overtone drill: finger a low note and overblow it to a higher partial (2 = octave, 3 = octave and
 * a fifth, 4 = two octaves). The expected frequency is the fingered note's concert frequency × the
 * partial number; a sounding frequency within [TOLERANCE_CENTS] held for [HOLD_SEC] counts.
 */
object OvertoneDrill {
    const val TOLERANCE_CENTS = 35.0
    const val HOLD_SEC = 1.0

    /** A target: the written fingering (low note) and the partial to sound. */
    data class Target(val writtenFingeringMidi: Int, val partial: Int)

    /** The milestone set: low B♭, B, C and D fingerings, 2nd and 3rd partials. */
    val milestone: List<Target> = listOf(
        Target(62, 2), Target(60, 2), Target(58, 2),
        Target(62, 3), Target(60, 3), Target(58, 3),
    )

    /** Partials needed for the milestone: all of them. */
    fun milestoneDone(done: Set<Target>) = milestone.all { it in done }

    fun expectedHz(t: Target, a4: Double, instrument: Instrument = Instrument.ALTO_SAX): Double =
        Tuning.midiToFrequency(instrument.writtenToConcertMidi(t.writtenFingeringMidi), a4) * t.partial

    /** Cents between a detected frequency and the target partial. */
    fun centsOff(detectedHz: Double, t: Target, a4: Double): Double = Tuning.cents(detectedHz, expectedHz(t, a4))

    fun matches(detectedHz: Double, t: Target, a4: Double): Boolean = abs(centsOff(detectedHz, t, a4)) <= TOLERANCE_CENTS

    /** The written note that sounds when overblowing (for display), rounded to the nearest semitone. */
    fun soundingWrittenMidi(t: Target, a4: Double, instrument: Instrument = Instrument.ALTO_SAX): Int =
        instrument.concertToWrittenMidi(Math.round(Tuning.frequencyToMidi(expectedHz(t, a4), a4)).toInt())
}

/**
 * Altissimo verification: the player holds a written note from G6 up for about two seconds; a pitch
 * within a generous tolerance writes a tuner-verified Range note.
 */
object AltissimoCheck {
    const val TOLERANCE_CENTS = 40.0
    const val HOLD_SEC = 2.0
    val TARGETS = 91..98 // written G6 .. D7

    fun matches(detectedHz: Double, writtenMidi: Int, a4: Double, instrument: Instrument = Instrument.ALTO_SAX): Boolean =
        abs(Tuning.cents(detectedHz, Tuning.midiToFrequency(instrument.writtenToConcertMidi(writtenMidi), a4))) <= TOLERANCE_CENTS
}
