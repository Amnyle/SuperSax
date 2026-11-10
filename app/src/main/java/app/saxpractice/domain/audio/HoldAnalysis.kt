package app.saxpractice.domain.audio

import app.saxpractice.domain.pitch.Tuning
import kotlin.math.abs
import kotlin.math.roundToInt

/** Parameters of hold detection and analysis. */
data class HoldConfig(
    /** Minimum YIN confidence for a frame to count as sound. */
    val minConfidence: Double = 0.80,
    /** A hold starts after this much continuous sound... */
    val onsetSec: Double = 0.150,
    /** ...and ends after this much continuous silence. */
    val releaseGapSec: Double = 0.200,
    /** Ignored at the start of a hold (attack: trackers are unreliable there). */
    val attackTrimSec: Double = 0.300,
    /** Ignored at the end of a hold (release). */
    val releaseTrimSec: Double = 0.200,
    val toleranceCents: Double = 10.0,
    /** Sampling rate of the stored trace. */
    val traceHz: Double = 10.0,
    val a4: Double = Tuning.DEFAULT_A4,
)

enum class PitchDirection { SHARP, FLAT, CENTERED }

/**
 * Result of one long-tone hold. Cents are relative to [refMidi] (concert). [trace] is the cents
 * offset downsampled to [traceHz], so thresholds can be recomputed later (see [inTuneShareAt]).
 */
data class HoldReport(
    val startSec: Double,
    val endSec: Double,
    val refMidi: Int,
    val stableSec: Double,
    val inTuneShare: Double,
    val toleranceCents: Double,
    val meanCents: Double,
    val driftCents: Double,
    val slopeCentsPerSec: Double,
    val minCents: Double,
    val maxCents: Double,
    val direction: PitchDirection,
    val trace: List<Float>,
    val traceHz: Double,
) {
    /** Seconds spent inside the tolerance. */
    val inTuneSec: Double get() = stableSec * inTuneShare

    /** Pitch at the end of the stable part, relative to the reference. */
    val endCents: Double get() = trace.takeLast(5).average().takeIf { !it.isNaN() } ?: meanCents

    companion object {
        /** Share of trace points within ±[toleranceCents]; used to re-evaluate stored holds. */
        fun inTuneShareAt(trace: List<Float>, toleranceCents: Double): Double =
            if (trace.isEmpty()) 0.0 else trace.count { abs(it) <= toleranceCents } / trace.size.toDouble()
    }
}

/**
 * Pure hold analysis over a stream of [PitchFrame]s.
 *
 * - Segmentation: a hold starts when frames are voiced (confidence ≥ [HoldConfig.minConfidence]) for
 *   [HoldConfig.onsetSec], and ends when they stop for [HoldConfig.releaseGapSec].
 * - The first [HoldConfig.attackTrimSec] and last [HoldConfig.releaseTrimSec] are ignored.
 * - The reference is the target note if given, else the semitone nearest the median of the stable part.
 */
object HoldAnalysis {

    private fun isSound(f: PitchFrame, c: HoldConfig) = f.frequencyHz != null && f.confidence >= c.minConfidence

    /**
     * Finds holds as (first frame index, last frame index) ranges. Gaps shorter than the release gap
     * are bridged (a hold survives a dropped frame).
     */
    fun segment(frames: List<PitchFrame>, c: HoldConfig = HoldConfig()): List<IntRange> {
        val holds = mutableListOf<IntRange>()
        var runStart = -1
        var lastSound = -1
        var inHold = false
        frames.forEachIndexed { i, f ->
            if (isSound(f, c)) {
                if (runStart == -1) runStart = i
                lastSound = i
                if (!inHold && f.timeSec - frames[runStart].timeSec >= c.onsetSec) inHold = true
            } else if (lastSound >= 0 && f.timeSec - frames[lastSound].timeSec >= c.releaseGapSec) {
                if (inHold) holds += runStart..lastSound
                inHold = false; runStart = -1; lastSound = -1
            }
        }
        if (inHold) holds += runStart..lastSound
        return holds
    }

    /** Analyses one hold (frames already segmented). Returns null if nothing stable remains after trimming. */
    fun analyse(frames: List<PitchFrame>, targetMidi: Int? = null, c: HoldConfig = HoldConfig()): HoldReport? {
        val sound = frames.filter { isSound(it, c) }
        if (sound.isEmpty()) return null
        val start = sound.first().timeSec
        val end = sound.last().timeSec
        val stable = sound.filter { it.timeSec >= start + c.attackTrimSec && it.timeSec <= end - c.releaseTrimSec }
        if (stable.size < 2) return null
        val midis = stable.map { Tuning.frequencyToMidi(it.frequencyHz!!, c.a4) }
        val ref = targetMidi ?: midis.sorted()[midis.size / 2].roundToInt()
        val refHz = Tuning.midiToFrequency(ref, c.a4)
        val cents = stable.map { Tuning.cents(it.frequencyHz!!, refHz) }
        val times = stable.map { it.timeSec }
        val mean = cents.average()
        val stableSec = times.last() - times.first()
        val window = minOf(0.5, stableSec / 3)
        val head = cents.filterIndexed { i, _ -> times[i] <= times.first() + window }.average()
        val tail = cents.filterIndexed { i, _ -> times[i] >= times.last() - window }.average()
        val slope = linearSlope(times, cents)
        val tol = c.toleranceCents
        return HoldReport(
            startSec = start, endSec = end, refMidi = ref, stableSec = stableSec,
            inTuneShare = cents.count { abs(it) <= tol } / cents.size.toDouble(),
            toleranceCents = tol, meanCents = mean, driftCents = tail - head, slopeCentsPerSec = slope,
            minCents = cents.min(), maxCents = cents.max(),
            direction = when { mean > tol / 2 -> PitchDirection.SHARP; mean < -tol / 2 -> PitchDirection.FLAT; else -> PitchDirection.CENTERED },
            trace = downsample(times, cents, c.traceHz), traceHz = c.traceHz,
        )
    }

    /** Segments [frames] and analyses every hold found. */
    fun analyseAll(frames: List<PitchFrame>, targetMidi: Int? = null, c: HoldConfig = HoldConfig()): List<HoldReport> =
        segment(frames, c).mapNotNull { r -> analyse(frames.subList(r.first, r.last + 1), targetMidi, c) }

    /** Least-squares slope (y per x unit). */
    fun linearSlope(x: List<Double>, y: List<Double>): Double {
        val mx = x.average(); val my = y.average()
        var num = 0.0; var den = 0.0
        for (i in x.indices) { num += (x[i] - mx) * (y[i] - my); den += (x[i] - mx) * (x[i] - mx) }
        return if (den == 0.0) 0.0 else num / den
    }

    /** Averages values into bins of 1/[hz] seconds. */
    fun downsample(times: List<Double>, values: List<Double>, hz: Double): List<Float> {
        if (times.isEmpty()) return emptyList()
        val t0 = times.first()
        return values.indices.groupBy { ((times[it] - t0) * hz).toInt() }.toSortedMap().values.map { idx -> idx.map { values[it] }.average().toFloat() }
    }
}

/**
 * Incremental hold detection for the live tuner: feed frames, get a report each time a hold ends.
 */
class HoldTracker(private val config: HoldConfig = HoldConfig(), var targetMidi: Int? = null) {
    private val frames = mutableListOf<PitchFrame>()
    private var inHold = false
    private var runStartIndex = -1
    private var lastSoundIndex = -1

    /** True while a hold is in progress (after the onset time). */
    val holding: Boolean get() = inHold

    /** Seconds since the current hold began, or 0. */
    fun holdSeconds(): Double = if (inHold && runStartIndex >= 0 && lastSoundIndex >= 0) frames[lastSoundIndex].timeSec - frames[runStartIndex].timeSec else 0.0

    fun push(f: PitchFrame): HoldReport? {
        frames += f
        val i = frames.lastIndex
        val sound = f.frequencyHz != null && f.confidence >= config.minConfidence
        if (sound) {
            if (runStartIndex == -1) runStartIndex = i
            lastSoundIndex = i
            if (!inHold && f.timeSec - frames[runStartIndex].timeSec >= config.onsetSec) inHold = true
            return null
        }
        if (runStartIndex == -1) { frames.clear(); return null }
        if (lastSoundIndex >= 0 && f.timeSec - frames[lastSoundIndex].timeSec >= config.releaseGapSec) {
            val report = if (inHold) HoldAnalysis.analyse(frames.subList(runStartIndex, lastSoundIndex + 1).toList(), targetMidi, config) else null
            reset()
            return report
        }
        return null
    }

    fun reset() { frames.clear(); inHold = false; runStartIndex = -1; lastSoundIndex = -1 }
}
