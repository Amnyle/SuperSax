package app.supersax.domain.library

import app.supersax.domain.pitch.Instrument
import app.supersax.domain.pitch.Interval
import app.supersax.domain.pitch.KeySignature
import app.supersax.domain.pitch.SpelledPitch

/** An exact fraction of a whole note (1/4 = quarter note). */
data class Fraction(val num: Int, val den: Int) : Comparable<Fraction> {
    init { require(den > 0) }
    val value: Double get() = num.toDouble() / den

    operator fun plus(o: Fraction): Fraction = Fraction(num * o.den + o.num * den, den * o.den).reduced()
    operator fun minus(o: Fraction): Fraction = Fraction(num * o.den - o.num * den, den * o.den).reduced()
    fun reduced(): Fraction { val g = gcd(kotlin.math.abs(num), den).coerceAtLeast(1); return Fraction(num / g, den / g) }
    override fun compareTo(other: Fraction): Int = (num.toLong() * other.den).compareTo(other.num.toLong() * den)
    override fun toString() = "$num/$den"

    companion object {
        val ZERO = Fraction(0, 1)
        fun parse(s: String): Fraction { val (a, b) = s.split("/"); return Fraction(a.toInt(), b.toInt()).reduced() }
        private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }
}

/** One note or rest ([pitch] null) of a single-voice score. [tie] ties it to the next note. */
data class ScoreNote(val pitch: SpelledPitch?, val duration: Fraction, val tie: Boolean = false) {
    val isRest: Boolean get() = pitch == null
}

data class Measure(val notes: List<ScoreNote>) {
    val length: Fraction get() = notes.fold(Fraction.ZERO) { acc, n -> acc + n.duration }
}

/**
 * A single-voice score in one key and time signature. Pitches are concert (storage standard) unless
 * the score was produced by [toWritten].
 */
data class Score(
    val keyFifths: Int,
    val timeNum: Int,
    val timeDen: Int,
    val tempo: Int,
    val measures: List<Measure>,
    /** The first measure is an incomplete pickup (anacrusis). */
    val pickup: Boolean = false,
) {
    val barLength: Fraction get() = Fraction(timeNum, timeDen)
    val notes: List<ScoreNote> get() = measures.flatMap { it.notes }
    val keySignature: KeySignature get() = KeySignature(keyFifths)

    /** Same music in written pitch for [instrument] (key and notes transposed, sharp keys respelled). */
    fun toWritten(instrument: Instrument = Instrument.ALTO_SAX): Score {
        val interval: Interval = instrument.writtenInterval(keyFifths)
        return copy(
            keyFifths = instrument.writtenKey(KeySignature(keyFifths)).fifths,
            measures = measures.map { m -> Measure(m.notes.map { n -> n.copy(pitch = n.pitch?.transpose(interval)) }) },
        )
    }

    companion object {
        /**
         * Parses the compact measure format used in piece files: space-separated tokens
         * `PITCH:DURATION`, e.g. `Eb4:1/4`, `r:1/2` (rest), `C5:3/8~` (dotted quarter tied to the next note).
         */
        fun parseMeasure(text: String): Measure = Measure(
            text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { token ->
                val tie = token.endsWith("~")
                val t = token.removeSuffix("~")
                val (p, d) = t.split(":")
                ScoreNote(if (p == "r") null else SpelledPitch.parse(p), Fraction.parse(d), tie)
            },
        )

        fun formatMeasure(m: Measure): String = m.notes.joinToString(" ") { n ->
            (n.pitch?.toString() ?: "r") + ":" + n.duration + if (n.tie) "~" else ""
        }
    }
}
