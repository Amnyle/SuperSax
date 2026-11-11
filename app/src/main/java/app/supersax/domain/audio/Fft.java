package app.supersax.domain.audio;

/** In-place iterative radix-2 complex FFT. {@code re} and {@code im} must have the same power-of-two length. */
public final class Fft {
    private Fft() {}

    public static void transform(double[] re, double[] im, boolean inverse) {
        int n = re.length;
        if (n != im.length || (n & (n - 1)) != 0) throw new IllegalArgumentException("size must be a power of two");
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            while ((j & bit) != 0) { j ^= bit; bit >>= 1; }
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len * (inverse ? 1 : -1);
            double wRe = Math.cos(ang), wIm = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double curRe = 1.0, curIm = 0.0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = a + len / 2;
                    double vRe = re[b] * curRe - im[b] * curIm;
                    double vIm = re[b] * curIm + im[b] * curRe;
                    re[b] = re[a] - vRe; im[b] = im[a] - vIm;
                    re[a] += vRe; im[a] += vIm;
                    double nRe = curRe * wRe - curIm * wIm;
                    curIm = curRe * wIm + curIm * wRe;
                    curRe = nRe;
                }
            }
        }
        if (inverse) for (int k = 0; k < n; k++) { re[k] /= n; im[k] /= n; }
    }

    public static void transform(double[] re, double[] im) { transform(re, im, false); }

    public static int nextPow2(int n) { int p = 1; while (p < n) p <<= 1; return p; }
}
