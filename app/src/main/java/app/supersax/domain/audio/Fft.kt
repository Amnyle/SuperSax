package app.supersax.domain.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place iterative radix-2 complex FFT. [re] and [im] must have the same power-of-two length. */
object Fft {
    fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean = false) {
        val n = re.size
        require(n == im.size && n and (n - 1) == 0) { "size must be a power of two" }
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = 2 * PI / len * if (inverse) 1 else -1
            val wRe = cos(ang); val wIm = sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0; var curIm = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val vRe = re[b] * curRe - im[b] * curIm
                    val vIm = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - vRe; im[b] = im[a] - vIm
                    re[a] += vRe; im[a] += vIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) for (k in 0 until n) { re[k] /= n; im[k] /= n }
    }

    fun nextPow2(n: Int): Int { var p = 1; while (p < n) p = p shl 1; return p }
}
