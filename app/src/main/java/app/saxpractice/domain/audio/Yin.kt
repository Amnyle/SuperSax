package app.saxpractice.domain.audio

import kotlin.math.sqrt

/** One analysis frame: time of the frame centre, detected frequency (null if unvoiced), confidence, level. */
data class PitchFrame(val timeSec: Double, val frequencyHz: Double?, val confidence: Double, val rms: Double) {
    val voiced: Boolean get() = frequencyHz != null
}

/**
 * YIN fundamental-frequency estimator (de Cheveigné & Kawahara, 2002), in pure Kotlin.
 *
 * Steps: (1) difference function d(τ), (2) cumulative mean normalised difference d'(τ),
 * (3) absolute threshold: the first τ whose d' falls under [threshold], followed down to its local
 * minimum (taking the first dip avoids sub-octave errors), (4) parabolic interpolation on d around
 * that τ for sub-sample precision. Confidence is `1 - d'(τ)`.
 *
 * Defaults (see docs/DECISIONS.md): 44.1 kHz, 2048-sample window (46 ms) which holds at least two
 * periods of 100 Hz (lowest alto note concert D♭3 ≈ 139 Hz), search 100–1500 Hz (into altissimo),
 * threshold 0.12, RMS gate 0.01 (about −40 dBFS).
 */
class Yin(
    val sampleRate: Int = 44_100,
    val windowSize: Int = 3072,
    val threshold: Double = 0.12,
    minFrequency: Double = 100.0,
    maxFrequency: Double = 1500.0,
    val rmsGate: Double = 0.01,
) {
    private val tauMin = (sampleRate / maxFrequency).toInt().coerceAtLeast(2)
    private val tauMax = (sampleRate / minFrequency).toInt().coerceAtMost(windowSize / 2)
    /** Half-width of the interpolation neighbourhood at the largest lag. */
    private val kMax = (tauMax / FIT_DIVISOR).coerceAtLeast(1)
    private val lastLag = tauMax + kMax + 1
    private val integration = windowSize - lastLag
    private val diff = DoubleArray(lastLag + 1)
    private val cmnd = DoubleArray(lastLag + 1)

    data class Result(val frequencyHz: Double?, val confidence: Double, val rms: Double)

    companion object {
        /** Largest interpolation half-width is τmax / 8 lags. */
        const val FIT_DIVISOR = 8
        const val QUADRATIC_RISE = 0.2
    }

    private val fftSize = Fft.nextPow2(windowSize + integration)
    private val aRe = DoubleArray(fftSize); private val aIm = DoubleArray(fftSize)
    private val bRe = DoubleArray(fftSize); private val bIm = DoubleArray(fftSize)
    private val prefix = DoubleArray(windowSize + 1)

    /** Energy reference for the interpolation width: d(τ) of a sine peaks at about 4·e1. */
    private var energy = 0.0

    private fun threePoint(tau: Int): Double {
        val s0 = diff[tau - 1]; val s1 = diff[tau]; val s2 = diff[tau + 1]
        val denom = 2 * (2 * s1 - s2 - s0)
        return if (denom != 0.0) tau + ((s2 - s0) / denom).coerceIn(-1.0, 1.0) else tau.toDouble()
    }

    /**
     * Sub-sample position of the dip. The fit uses ±k lags, where k grows while the curve stays in its
     * quadratic region (rise under 20 percent of the signal energy): wide for the smooth dips of low
     * notes (which averages out noise), 1 for sharp dips of harmonic-rich high notes.
     */
    private fun refine(tau: Int): Double {
        if (tau < 2) return tau.toDouble()
        val base = threePoint(tau)
        var k = 1
        while (k < kMax && tau - k - 1 >= 1 &&
            maxOf(diff[tau + k + 1], diff[tau - k - 1]) - diff[tau] <= QUADRATIC_RISE * energy
        ) k++
        if (k < 3) return base
        var sx2 = 0.0; var sx4 = 0.0; var sy = 0.0; var sxy = 0.0; var sx2y = 0.0
        for (x in -k..k) {
            val y = diff[tau + x]; val xd = x.toDouble(); val x2 = xd * xd
            sx2 += x2; sx4 += x2 * x2; sy += y; sxy += xd * y; sx2y += x2 * y
        }
        val n = 2 * k + 1
        val a = (sx2y - sx2 * sy / n) / (sx4 - sx2 * sx2 / n)
        val b = sxy / sx2
        if (a <= 0) return base
        val offset = -b / (2 * a)
        val limit = maxOf(1.0, k / 2.0)
        return if (offset in -limit..limit) tau + offset else base
    }

    /**
     * d(τ) = Σ_{j<N} (x_j − x_{j+τ})² for τ in 0..τmax+1, N = [integration]. Expanded into two energy
     * terms (prefix sums) and a cross-correlation computed with one forward FFT per input and one inverse.
     */
    private fun differenceFft(x: FloatArray, offset: Int) {
        aRe.fill(0.0); aIm.fill(0.0); bRe.fill(0.0); bIm.fill(0.0)
        for (j in 0 until integration) aRe[j] = x[offset + j].toDouble()
        for (j in 0 until windowSize) bRe[j] = x[offset + j].toDouble()
        prefix[0] = 0.0
        for (j in 0 until windowSize) prefix[j + 1] = prefix[j] + bRe[j] * bRe[j]
        Fft.transform(aRe, aIm)
        Fft.transform(bRe, bIm)
        // conj(A) · B
        for (k in 0 until fftSize) {
            val re = aRe[k] * bRe[k] + aIm[k] * bIm[k]
            val im = aRe[k] * bIm[k] - aIm[k] * bRe[k]
            bRe[k] = re; bIm[k] = im
        }
        Fft.transform(bRe, bIm, inverse = true)
        val e1 = prefix[integration]
        energy = e1
        for (tau in 0..lastLag) {
            val e2 = prefix[tau + integration] - prefix[tau]
            diff[tau] = (e1 + e2 - 2 * bRe[tau]).coerceAtLeast(0.0)
        }
    }

    /** Analyses [windowSize] samples of [samples] starting at [offset]. */
    fun detect(samples: FloatArray, offset: Int = 0): Result {
        require(offset + windowSize <= samples.size) { "need $windowSize samples" }
        var sumSq = 0.0
        for (i in offset until offset + windowSize) sumSq += samples[i] * samples[i].toDouble()
        val rms = sqrt(sumSq / windowSize)
        if (rms < rmsGate) return Result(null, 0.0, rms)

        // 1. Difference function d(τ) = e1 + e2(τ) − 2·r(τ), with the cross term r computed by FFT.
        differenceFft(samples, offset)
        // 2. Cumulative mean normalised difference.
        cmnd[0] = 1.0
        var running = 0.0
        for (tau in 1..tauMax + 1) {
            running += diff[tau]
            cmnd[tau] = if (running == 0.0) 1.0 else diff[tau] * tau / running
        }
        // 3. Absolute threshold, then follow the dip to its minimum.
        var tau = -1
        var t = tauMin
        while (t <= tauMax) {
            if (cmnd[t] < threshold) {
                while (t + 1 <= tauMax && cmnd[t + 1] < cmnd[t]) t++
                tau = t
                break
            }
            t++
        }
        if (tau == -1) {
            // No dip under the threshold: report the global minimum with its (low) confidence.
            var best = tauMin
            for (k in tauMin..tauMax) if (cmnd[k] < cmnd[best]) best = k
            val conf = (1.0 - cmnd[best]).coerceIn(0.0, 1.0)
            return Result(null, conf, rms)
        }
        // 3b. Noise puts small wiggles on d(τ): the walk above can stop a few lags early. Take the true
        // minimum of d within ±τ/16 (well inside one period, so it cannot jump to another dip).
        run {
            val w = (tau / 16).coerceAtLeast(0)
            var best = tau
            for (k in (tau - w).coerceAtLeast(tauMin)..(tau + w).coerceAtMost(tauMax)) if (diff[k] < diff[best]) best = k
            tau = best
        }
        // 4. Quadratic interpolation on the raw difference function. Low pitches have a wide, shallow
        // dip, so the parabola is a least-squares fit over ±k lags with k growing with τ (k = 1 at the top).
        val betterTau = refine(tau)
        val confidence = (1.0 - cmnd[tau]).coerceIn(0.0, 1.0)
        return Result(sampleRate / betterTau, confidence, rms)
    }
}

/**
 * Streams audio into overlapping YIN windows (hop [hop]) and smooths the voiced frequencies with a
 * running median over [medianFrames] frames (in the log-frequency domain), which removes single-frame
 * glitches without lagging much (5 × 11.6 ms).
 */
class PitchTracker(
    private val yin: Yin = Yin(),
    val hop: Int = 512,
    private val medianFrames: Int = 5,
) {
    private val buffer = FloatArray(yin.windowSize)
    private var filled = 0
    private var samplesSeen = 0L
    private val recent = ArrayDeque<Double>()

    private var pendingHop = 0

    /** Feeds samples; returns one frame per completed hop (the window slides as samples arrive). */
    fun push(samples: FloatArray, count: Int = samples.size): List<PitchFrame> {
        val out = mutableListOf<PitchFrame>()
        val w = yin.windowSize
        var i = 0
        while (i < count) {
            if (filled < w) {
                val take = minOf(w - filled, count - i)
                System.arraycopy(samples, i, buffer, filled, take)
                filled += take; i += take; samplesSeen += take
                if (filled == w) out += analyse()
            } else {
                val take = minOf(hop - pendingHop, count - i)
                System.arraycopy(buffer, take, buffer, 0, w - take)
                System.arraycopy(samples, i, buffer, w - take, take)
                i += take; samplesSeen += take; pendingHop += take
                if (pendingHop == hop) { pendingHop = 0; out += analyse() }
            }
        }
        return out
    }

    private fun analyse(): PitchFrame {
        val r = yin.detect(buffer)
        val time = (samplesSeen - yin.windowSize / 2).toDouble() / yin.sampleRate
        val f = r.frequencyHz
        val smoothed = if (f != null) {
            recent.addLast(kotlin.math.ln(f))
            while (recent.size > medianFrames) recent.removeFirst()
            kotlin.math.exp(recent.sorted()[recent.size / 2])
        } else {
            recent.clear()
            null
        }
        return PitchFrame(time, smoothed, r.confidence, r.rms)
    }

    fun reset() { filled = 0; samplesSeen = 0; pendingHop = 0; recent.clear() }
}
