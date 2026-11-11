package app.supersax.domain

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Synthesised test signals. */
object AudioSignals {
    const val SR = 44_100

    /** Sum of harmonics with the given amplitudes (index 0 = fundamental), plus white noise. */
    fun tone(freq: Double, seconds: Double, harmonics: List<Double> = listOf(1.0), amp: Double = 0.5, noise: Double = 0.0, seed: Int = 1): FloatArray {
        val n = (seconds * SR).toInt()
        val rnd = Random(seed)
        val norm = harmonics.sum()
        return FloatArray(n) { i ->
            var s = 0.0
            harmonics.forEachIndexed { h, a -> s += a * sin(2 * PI * freq * (h + 1) * i / SR) }
            (amp * s / norm + noise * (rnd.nextDouble() * 2 - 1)).toFloat()
        }
    }

    /** Frequency gliding linearly in cents over time. */
    fun drifting(freq: Double, seconds: Double, centsStart: Double, centsEnd: Double, vibratoCents: Double = 0.0, vibratoHz: Double = 5.0): FloatArray {
        val n = (seconds * SR).toInt()
        var phase = 0.0
        return FloatArray(n) { i ->
            val t = i.toDouble() / SR
            val cents = centsStart + (centsEnd - centsStart) * t / seconds + vibratoCents * sin(2 * PI * vibratoHz * t)
            val f = freq * Math.pow(2.0, cents / 1200)
            phase += 2 * PI * f / SR
            (0.5 * sin(phase)).toFloat()
        }
    }

    fun silence(seconds: Double) = FloatArray((seconds * SR).toInt())

    operator fun FloatArray.plus(other: FloatArray): FloatArray = FloatArray(size + other.size) { if (it < size) this[it] else other[it - size] }
}
