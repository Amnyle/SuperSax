package app.supersax.domain.song;

import app.supersax.domain.audio.Fft;

/**
 * Key of a recording (concert pitch): a chroma profile summed over the whole recording (4096-sample
 * frames, energy from 65 Hz to 2 kHz folded onto the 12 pitch classes), compared with the
 * Krumhansl–Kessler major and minor key profiles; the best correlation of the 24 keys wins.
 */
public final class KeyDetector {
    private KeyDetector() {}

    public static final int FRAME = 4096, HOP = 2048;
    static final double[] MAJOR = {6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88};
    static final double[] MINOR = {6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17};

    /** Tonic pitch class (0 = C), major or minor, and how far ahead of the runner-up (0..1). */
    public static final class Result {
        private final int tonic;
        private final boolean minor;
        private final double confidence;
        public Result(int tonic, boolean minor, double confidence) { this.tonic = tonic; this.minor = minor; this.confidence = confidence; }
        public int getTonic() { return tonic; }
        public boolean getMinor() { return minor; }
        public double getConfidence() { return confidence; }
    }

    public static double[] chroma(float[] x, int sampleRate) {
        double[] chroma = new double[12];
        double[] window = new double[FRAME];
        for (int i = 0; i < FRAME; i++) window[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / FRAME);
        int[] pcOfBin = new int[FRAME / 2];
        for (int k = 0; k < FRAME / 2; k++) {
            double f = (double) k * sampleRate / FRAME;
            pcOfBin[k] = f < 65 || f > 2000 ? -1 : Math.floorMod((int) Math.round(12 * Math.log(f / 440.0) / Math.log(2)) + 9, 12);
        }
        double[] re = new double[FRAME], im = new double[FRAME];
        for (int off = 0; off + FRAME <= x.length; off += HOP) {
            for (int i = 0; i < FRAME; i++) { re[i] = x[off + i] * window[i]; im[i] = 0; }
            Fft.transform(re, im);
            double[] frame = new double[12];
            double energy = 0;
            for (int k = 1; k < FRAME / 2; k++) {
                if (pcOfBin[k] < 0) continue;
                double m = re[k] * re[k] + im[k] * im[k];
                frame[pcOfBin[k]] += m;
                energy += m;
            }
            // Each frame counts by its shape, not its loudness (quiet passages matter as much).
            if (energy > 1e-9) for (int p = 0; p < 12; p++) chroma[p] += Math.sqrt(frame[p] / energy);
        }
        return chroma;
    }

    static double correlation(double[] a, double[] b, int rotate) {
        double ma = 0, mb = 0;
        for (int i = 0; i < 12; i++) { ma += a[i]; mb += b[i]; }
        ma /= 12; mb /= 12;
        double num = 0, da = 0, db = 0;
        for (int i = 0; i < 12; i++) {
            double x = a[(i + rotate) % 12] - ma, y = b[i] - mb;
            num += x * y; da += x * x; db += y * y;
        }
        return da == 0 || db == 0 ? 0 : num / Math.sqrt(da * db);
    }

    public static Result fromChroma(double[] chroma) {
        int bestTonic = 0;
        boolean bestMinor = false;
        double best = -2, second = -2;
        for (int tonic = 0; tonic < 12; tonic++) {
            for (int m = 0; m < 2; m++) {
                double r = correlation(chroma, m == 0 ? MAJOR : MINOR, tonic);
                if (r > best) { second = best; best = r; bestTonic = tonic; bestMinor = m == 1; }
                else if (r > second) second = r;
            }
        }
        return new Result(bestTonic, bestMinor, Math.max(0, Math.min(1, (best - second) * 5)));
    }

    public static Result detect(float[] x, int sampleRate) { return fromChroma(chroma(x, sampleRate)); }
}
