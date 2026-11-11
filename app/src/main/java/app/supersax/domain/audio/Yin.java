package app.supersax.domain.audio;

import java.util.Arrays;
import org.jetbrains.annotations.Nullable;

/**
 * YIN fundamental-frequency estimator (de Cheveigné & Kawahara, 2002).
 *
 * <p>Steps: (1) difference function d(τ), computed with an FFT cross-correlation; (2) cumulative mean
 * normalised difference d'(τ); (3) absolute threshold: the first τ whose d' falls under the threshold,
 * followed down to its local minimum (taking the first dip avoids sub-octave errors), then the true
 * minimum of d within ±τ/16 (noise puts small wiggles on d); (4) least-squares parabola on d around
 * that τ, over a neighbourhood that grows while d stays quadratic. Confidence is {@code 1 - d'(τ)}.
 *
 * <p>Defaults (docs/DECISIONS.md): 44.1 kHz, 3072-sample window (70 ms), search 100–1500 Hz (lowest
 * alto note concert D♭3 ≈ 139 Hz up into altissimo), threshold 0.12, RMS gate 0.01 (about −40 dBFS).
 */
public final class Yin {
    /** Largest interpolation half-width is τmax / 8 lags. */
    public static final int FIT_DIVISOR = 8;
    public static final double QUADRATIC_RISE = 0.2;

    /** Detection result: frequency (null if unvoiced), confidence 0..1 and RMS level. */
    public static final class Result {
        private final @Nullable Double frequencyHz;
        private final double confidence;
        private final double rms;

        public Result(@Nullable Double frequencyHz, double confidence, double rms) {
            this.frequencyHz = frequencyHz;
            this.confidence = confidence;
            this.rms = rms;
        }

        public @Nullable Double getFrequencyHz() { return frequencyHz; }

        public double getConfidence() { return confidence; }

        public double getRms() { return rms; }
    }

    private final int sampleRate;
    private final int windowSize;
    private final double threshold;
    private final double rmsGate;
    private final int tauMin;
    private final int tauMax;
    private final int kMax;
    private final int lastLag;
    private final int integration;
    private final double[] diff;
    private final double[] cmnd;
    private final int fftSize;
    private final double[] aRe, aIm, bRe, bIm;
    private final double[] prefix;
    /** Energy reference for the interpolation width. */
    private double energy;

    public Yin(int sampleRate, int windowSize, double threshold, double minFrequency, double maxFrequency, double rmsGate) {
        this.sampleRate = sampleRate;
        this.windowSize = windowSize;
        this.threshold = threshold;
        this.rmsGate = rmsGate;
        this.tauMin = Math.max(2, (int) (sampleRate / maxFrequency));
        this.tauMax = Math.min(windowSize / 2, (int) (sampleRate / minFrequency));
        this.kMax = Math.max(1, tauMax / FIT_DIVISOR);
        this.lastLag = tauMax + kMax + 1;
        this.integration = windowSize - lastLag;
        this.diff = new double[lastLag + 1];
        this.cmnd = new double[lastLag + 1];
        this.fftSize = Fft.nextPow2(windowSize + integration);
        this.aRe = new double[fftSize]; this.aIm = new double[fftSize];
        this.bRe = new double[fftSize]; this.bIm = new double[fftSize];
        this.prefix = new double[windowSize + 1];
    }

    public Yin(int sampleRate) { this(sampleRate, 3072, 0.12, 100.0, 1500.0, 0.01); }

    public Yin() { this(44_100); }

    public int getSampleRate() { return sampleRate; }

    public int getWindowSize() { return windowSize; }

    public double getThreshold() { return threshold; }

    public double getRmsGate() { return rmsGate; }

    private double threePoint(int tau) {
        double s0 = diff[tau - 1], s1 = diff[tau], s2 = diff[tau + 1];
        double denom = 2 * (2 * s1 - s2 - s0);
        if (denom == 0.0) return tau;
        return tau + Math.max(-1.0, Math.min(1.0, (s2 - s0) / denom));
    }

    /** Sub-sample position of the dip (see class comment). */
    private double refine(int tau) {
        if (tau < 2) return tau;
        double base = threePoint(tau);
        int k = 1;
        while (k < kMax && tau - k - 1 >= 1
            && Math.max(diff[tau + k + 1], diff[tau - k - 1]) - diff[tau] <= QUADRATIC_RISE * energy) k++;
        if (k < 3) return base;
        double sx2 = 0, sx4 = 0, sy = 0, sxy = 0, sx2y = 0;
        for (int x = -k; x <= k; x++) {
            double y = diff[tau + x], x2 = (double) x * x;
            sx2 += x2; sx4 += x2 * x2; sy += y; sxy += x * y; sx2y += x2 * y;
        }
        int n = 2 * k + 1;
        double a = (sx2y - sx2 * sy / n) / (sx4 - sx2 * sx2 / n);
        double b = sxy / sx2;
        if (a <= 0) return base;
        double offset = -b / (2 * a);
        double limit = Math.max(1.0, k / 2.0);
        return offset >= -limit && offset <= limit ? tau + offset : base;
    }

    /** d(τ) = Σ_{j<N} (x_j − x_{j+τ})² = e1 + e2(τ) − 2·r(τ), with r computed by FFT. */
    private void differenceFft(float[] x, int offset) {
        Arrays.fill(aRe, 0); Arrays.fill(aIm, 0); Arrays.fill(bRe, 0); Arrays.fill(bIm, 0);
        for (int j = 0; j < integration; j++) aRe[j] = x[offset + j];
        for (int j = 0; j < windowSize; j++) bRe[j] = x[offset + j];
        prefix[0] = 0;
        for (int j = 0; j < windowSize; j++) prefix[j + 1] = prefix[j] + bRe[j] * bRe[j];
        Fft.transform(aRe, aIm);
        Fft.transform(bRe, bIm);
        for (int k = 0; k < fftSize; k++) { // conj(A) · B
            double re = aRe[k] * bRe[k] + aIm[k] * bIm[k];
            double im = aRe[k] * bIm[k] - aIm[k] * bRe[k];
            bRe[k] = re; bIm[k] = im;
        }
        Fft.transform(bRe, bIm, true);
        double e1 = prefix[integration];
        energy = e1;
        for (int tau = 0; tau <= lastLag; tau++) {
            double e2 = prefix[tau + integration] - prefix[tau];
            diff[tau] = Math.max(0.0, e1 + e2 - 2 * bRe[tau]);
        }
    }

    /** Analyses {@code windowSize} samples starting at {@code offset}. */
    public Result detect(float[] samples, int offset) {
        if (offset + windowSize > samples.length) throw new IllegalArgumentException("need " + windowSize + " samples");
        double sumSq = 0;
        for (int i = offset; i < offset + windowSize; i++) sumSq += samples[i] * (double) samples[i];
        double rms = Math.sqrt(sumSq / windowSize);
        if (rms < rmsGate) return new Result(null, 0.0, rms);

        differenceFft(samples, offset);
        cmnd[0] = 1.0;
        double running = 0;
        for (int tau = 1; tau <= tauMax + 1; tau++) {
            running += diff[tau];
            cmnd[tau] = running == 0.0 ? 1.0 : diff[tau] * tau / running;
        }
        int tau = -1;
        for (int t = tauMin; t <= tauMax; t++) {
            if (cmnd[t] < threshold) {
                while (t + 1 <= tauMax && cmnd[t + 1] < cmnd[t]) t++;
                tau = t;
                break;
            }
        }
        if (tau == -1) {
            // No dip under the threshold: report the global minimum with its (low) confidence.
            int best = tauMin;
            for (int k = tauMin; k <= tauMax; k++) if (cmnd[k] < cmnd[best]) best = k;
            return new Result(null, clamp01(1.0 - cmnd[best]), rms);
        }
        int w = Math.max(0, tau / 16);
        int best = tau;
        for (int k = Math.max(tauMin, tau - w); k <= Math.min(tauMax, tau + w); k++) if (diff[k] < diff[best]) best = k;
        tau = best;
        double betterTau = refine(tau);
        return new Result(sampleRate / betterTau, clamp01(1.0 - cmnd[tau]), rms);
    }

    public Result detect(float[] samples) { return detect(samples, 0); }

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }
}
