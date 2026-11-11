package app.saxpractice.domain.audio

import app.saxpractice.domain.pitch.Tuning
import kotlin.math.abs
import kotlin.math.roundToInt

/** A detected note: concert MIDI (rounded), start and end time. */
data class DetectedNote(val midi: Int, val startSec: Double, val endSec: Double) {
    val durationSec: Double get() = endSec - startSec
}

/** Result of comparing what was played with the expected scale. */
data class ScaleCheck(
    val played: List<DetectedNote>,
    val expected: List<Int>,
    val matched: Int,
    val wrong: Int,
    val missing: Int,
    val extra: Int,
    /** Notes per minute from the first onset to the last offset. */
    val notesPerMinute: Double,
) {
    /** A clean run: every expected note, in order, nothing wrong or extra. */
    val clean: Boolean get() = matched == expected.size && wrong == 0 && extra == 0 && missing == 0
}

/**
 * Experimental scale checker. Segments a pitch stream into notes with hysteresis (a new note starts
 * only when the pitch stays more than [changeCents] away from the current note for [confirmSec]),
 * drops notes shorter than [minNoteSec], then aligns the result with the expected sequence by edit
 * distance. It only reports: the player confirms before anything is logged.
 */
class ScaleChecker(
    private val changeCents: Double = 60.0,
    private val confirmSec: Double = 0.045,
    private val minNoteSec: Double = 0.07,
    private val a4: Double = Tuning.DEFAULT_A4,
    private val minConfidence: Double = 0.8,
) {
    fun segment(frames: List<PitchFrame>): List<DetectedNote> {
        val notes = mutableListOf<DetectedNote>()
        var curMidi: Double? = null
        var curStart = 0.0
        var lastTime = 0.0
        var candidate: Double? = null
        var candidateStart = 0.0
        val values = mutableListOf<Double>()
        fun close(end: Double) {
            curMidi ?: return
            if (end - curStart >= minNoteSec && values.isNotEmpty()) notes += DetectedNote(values.sorted()[values.size / 2].roundToInt(), curStart, end)
            curMidi = null; values.clear()
        }
        for (f in frames) {
            val hz = f.frequencyHz?.takeIf { f.confidence >= minConfidence }
            if (hz == null) {
                if (curMidi != null && f.timeSec - lastTime > confirmSec) close(lastTime)
                candidate = null
                continue
            }
            val m = Tuning.frequencyToMidi(hz, a4)
            val cur = curMidi
            if (cur == null) {
                curMidi = m; curStart = f.timeSec; values += m
            } else if (abs(m - cur) * 100 > changeCents) {
                if (candidate == null || abs(m - candidate!!) * 100 > changeCents) { candidate = m; candidateStart = f.timeSec }
                if (f.timeSec - candidateStart >= confirmSec) {
                    close(candidateStart)
                    curMidi = m; curStart = candidateStart; values += m
                    candidate = null
                }
            } else {
                candidate = null
                values += m
                // Follow slow drift: the reference is the running median.
                curMidi = values.sorted()[values.size / 2]
            }
            lastTime = f.timeSec
        }
        if (curMidi != null) close(lastTime)
        return notes
    }

    fun check(frames: List<PitchFrame>, expected: List<Int>): ScaleCheck {
        val played = segment(frames)
        val p = played.map { it.midi }
        // Edit distance with backtracking to count matches, substitutions, deletions and insertions.
        val n = expected.size; val m = p.size
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (expected[i - 1] == p[j - 1]) 0 else 1)
        }
        var i = n; var j = m
        var matched = 0; var wrong = 0; var missing = 0; var extra = 0
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + if (expected[i - 1] == p[j - 1]) 0 else 1) {
                if (expected[i - 1] == p[j - 1]) matched++ else wrong++
                i--; j--
            } else if (i > 0 && d[i][j] == d[i - 1][j] + 1) { missing++; i-- } else { extra++; j-- }
        }
        val span = if (played.size >= 2) played.last().endSec - played.first().startSec else 0.0
        val npm = if (span > 0) played.size / span * 60 else 0.0
        return ScaleCheck(played, expected, matched, wrong, missing, extra, npm)
    }
}
