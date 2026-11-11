package app.supersax.domain.library

import app.supersax.domain.common.LocalizedText
import app.supersax.domain.pitch.Instrument
import app.supersax.domain.pitch.KeySignature
import app.supersax.domain.scale.ScaleId
import app.supersax.domain.scale.ScaleType
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/** A section of a piece (bar range, 1-based inclusive), used by song-milestone goals. */
@Serializable
data class PieceSection(val id: String, val title: LocalizedText, val from: Int, val to: Int)

/** A piece file as stored in `assets/pieces/` (see docs/PIECE_FORMAT.md). Pitches are concert. */
@Serializable
data class PieceFile(
    val id: String,
    val title: LocalizedText,
    val composer: String,
    val source: String,
    val licence: String,
    val credit: String,
    val tags: List<String> = emptyList(),
    val reviewed: Boolean = false,
    val bundled: Boolean = true,
    val key: Int? = null,
    val mode: String = "major",
    val time: String? = null,
    val tempo: Int? = null,
    val pickup: Boolean = false,
    val measures: List<String> = emptyList(),
    val sections: List<PieceSection> = emptyList(),
)

/** A loaded piece: metadata, parsed score (null for metadata-only entries) and computed metrics. */
data class Piece(
    val file: PieceFile,
    val score: Score?,
    val difficulty: Int,
    /** Lowest and highest concert MIDI notes, or null. */
    val lowMidi: Int?,
    val highMidi: Int?,
    val durationSec: Int,
) {
    val id: String get() = file.id
    val hasNotes: Boolean get() = score != null && score.notes.any { !it.isRest }

    /** The scale of the piece's key (concert), from key signature and mode. */
    val scaleKey: String?
        get() {
            val k = file.key ?: return null
            val sig = KeySignature(k)
            val minor = file.mode == "minor"
            val tonic = if (minor) sig.minorTonic else sig.majorTonic
            val type = if (minor) ScaleType.NATURAL_MINOR else ScaleType.MAJOR
            return ScaleId(ScaleId.normaliseConcertTonic(tonic, type), type).key
        }

    /** The relative scale (relative minor of a major key and vice versa). */
    val relativeScaleKey: String?
        get() {
            val k = file.key ?: return null
            val sig = KeySignature(k)
            val minor = file.mode == "minor"
            val type = if (minor) ScaleType.MAJOR else ScaleType.NATURAL_MINOR
            val tonic = if (minor) sig.majorTonic else sig.minorTonic
            return ScaleId(ScaleId.normaliseConcertTonic(tonic, type), type).key
        }

    companion object {
        /** Parses a piece file and computes its metrics. */
        fun from(file: PieceFile, instrument: Instrument = Instrument.ALTO_SAX): Piece {
            val score = if (file.bundled && file.key != null && file.time != null && file.measures.isNotEmpty()) {
                val (num, den) = file.time.split("/").map { it.toInt() }
                Score(file.key, num, den, file.tempo ?: 80, file.measures.map { Score.parseMeasure(it) }, file.pickup)
            } else null
            val pitches = score?.notes?.mapNotNull { it.pitch?.midi } ?: emptyList()
            return Piece(
                file = file,
                score = score,
                difficulty = score?.let { Difficulty.score(it, instrument) } ?: 0,
                lowMidi = pitches.minOrNull(),
                highMidi = pitches.maxOrNull(),
                durationSec = score?.let { Difficulty.durationSec(it) } ?: 0,
            )
        }
    }
}

/**
 * Difficulty from 1 to 10, computed from the score as the alto player reads it (written pitch).
 *
 * Eight features, each with a maximum weight (sum 10.5), then scaled to 1..10:
 * - range span: 0 up to 7 semitones, full 1.5 at 24 or more;
 * - extremes: 0.75 if any note is under written D4 (low B♭–C♯), 0.75 if any is above written C6 (palm keys and up), +0.5 for altissimo;
 * - key signature size: |written fifths| / 6 × 1.5;
 * - accidentals outside the key: share of notes × 5, capped at 1.0;
 * - shortest note: quarter or longer 0, eighth 0.6, sixteenth or shorter 1.2;
 * - rhythm: dotted notes, ties and syncopation (notes starting off the beat and crossing it), up to 1.3;
 * - speed: notes per second, 0 at 1/s, full 2.0 at 5/s;
 * - length: 0 up to 8 bars, full 0.5 at 48 bars.
 */
object Difficulty {
    private const val MAX = 10.5

    fun features(score: Score, instrument: Instrument = Instrument.ALTO_SAX): Map<String, Double> {
        val written = score.toWritten(instrument)
        val notes = written.notes.filter { !it.isRest }
        if (notes.isEmpty()) return emptyMap()
        val midis = notes.map { it.pitch!!.midi }
        val span = midis.max() - midis.min()
        val fSpan = ((span - 7) / 17.0).coerceIn(0.0, 1.0) * 1.5
        var fExt = 0.0
        if (midis.any { it < 62 }) fExt += 0.75
        if (midis.any { it > 84 }) fExt += 0.75
        if (midis.any { it > instrument.writtenHighMidi }) fExt += 0.5
        val fKey = abs(written.keyFifths) / 6.0 * 1.5
        val key = KeySignature(written.keyFifths)
        val accidentals = notes.count { it.pitch!!.alter != key.alterFor(it.pitch.letter) }
        val fAcc = (accidentals.toDouble() / notes.size * 5).coerceAtMost(1.0)
        val shortest = written.notes.minOf { it.duration.value }
        val fShort = when {
            shortest >= 0.25 - 1e-9 -> 0.0
            shortest >= 0.125 - 1e-9 -> 0.6
            else -> 1.2
        }
        // Rhythm: dotted notes, ties, syncopations (relative to the beat 1/den).
        val beat = 1.0 / written.timeDen
        var pos = 0.0
        var syncopations = 0
        var dotted = 0
        var ties = 0
        written.measures.forEachIndexed { mi, m ->
            pos = 0.0
            m.notes.forEach { n ->
                val d = n.duration.value
                val offBeat = (pos / beat) % 1.0 > 1e-6
                val crosses = ((pos + d) / beat).let { end -> kotlin.math.floor(end - 1e-9) > kotlin.math.floor(pos / beat + 1e-9) }
                if (!n.isRest && offBeat && crosses) syncopations++
                if (n.duration.num == 3) dotted++
                if (n.tie) ties++
                pos += d
            }
            if (mi == 0 && written.pickup) pos = 0.0
        }
        val total = written.notes.size.toDouble()
        val fRhythm = ((dotted / total) * 2.0 + (ties / total) * 2.0 + (syncopations / total) * 4.0).coerceAtMost(1.3)
        val seconds = durationSec(score).coerceAtLeast(1)
        val nps = notes.size.toDouble() / seconds
        val fSpeed = ((nps - 1.0) / 4.0).coerceIn(0.0, 1.0) * 2.0
        val fLen = ((written.measures.size - 8) / 40.0).coerceIn(0.0, 1.0) * 0.5
        return mapOf(
            "span" to fSpan, "extremes" to fExt, "key" to fKey, "accidentals" to fAcc,
            "shortest" to fShort, "rhythm" to fRhythm, "speed" to fSpeed, "length" to fLen,
        )
    }

    fun score(score: Score, instrument: Instrument = Instrument.ALTO_SAX): Int {
        val f = features(score, instrument)
        if (f.isEmpty()) return 1
        return (1 + f.values.sum() * 9 / MAX).roundToInt().coerceIn(1, 10)
    }

    /** Playing time at the written tempo (beats of 1/den per minute). */
    fun durationSec(score: Score): Int {
        val wholes = score.measures.sumOf { it.length.value }
        val beats = wholes * score.timeDen
        return (beats * 60.0 / score.tempo).roundToInt()
    }
}
