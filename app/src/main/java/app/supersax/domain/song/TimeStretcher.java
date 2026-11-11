package app.supersax.domain.song;

import app.supersax.domain.audio.Fft;

/**
 * Changes the duration of audio without changing its pitch: a phase vocoder with identity phase
 * locking (Laroche and Dolson), the usual high-quality method for music. 4096-sample Hann frames,
 * synthesis hop 512 (8× overlap). Spectral peaks keep their true frequency (from the phase advance
 * between analysis frames); the bins around each peak keep their phase relation to it, which avoids
 * the "phasey" sound of a plain vocoder. Each channel is processed alike.
 *
 * <p>Streaming use: call {@link #synthesize} once per hop with the analysis position advancing by
 * {@code hop / stretch}; each call returns {@link #HOP} output frames.
 */
public final class TimeStretcher {
    public static final int N = 4096, HOP = 512;
    private static final double TWO_PI = 2 * Math.PI;

    /** Reads source samples; frames outside the recording read as 0. */
    public interface Source {
        float sample(int channel, long frame);
    }

    private final int channels;
    private final double[] window = new double[N];
    private final double norm;
    private final double[][] prevAnalysisPhase, prevSynthPhase, accumulator;
    private final double[] re = new double[N], im = new double[N], mag = new double[N / 2 + 1], phase = new double[N / 2 + 1];
    private final double[] synth = new double[N / 2 + 1];
    private final boolean[] peak = new boolean[N / 2 + 1];
    private final int[] owner = new int[N / 2 + 1];
    private long prevStart;
    private boolean first = true;

    public TimeStretcher(int channels) {
        this.channels = channels;
        for (int i = 0; i < N; i++) window[i] = 0.5 - 0.5 * Math.cos(TWO_PI * i / N);
        // Overlap-add of the squared window (analysis × synthesis) is constant: its value normalises the output.
        double sum = 0;
        for (int off = 0; off < HOP; off++) { double t = 0; for (int i = off; i < N; i += HOP) t += window[i] * window[i]; sum += t; }
        norm = sum / HOP;
        prevAnalysisPhase = new double[channels][N / 2 + 1];
        prevSynthPhase = new double[channels][N / 2 + 1];
        accumulator = new double[channels][N];
    }

    /** Forget all state (after a seek): the next frame starts fresh. */
    public void reset() {
        first = true;
        for (int c = 0; c < channels; c++) java.util.Arrays.fill(accumulator[c], 0);
    }

    private static double princarg(double p) { return p - TWO_PI * Math.round(p / TWO_PI); }

    /**
     * One synthesis hop: analyses the frame starting at [analysisPos] (rounded to a frame) and writes
     * {@link #HOP} output frames per channel into [out][c][offset..offset+HOP).
     */
    public void synthesize(Source src, double analysisPos, float[][] out, int offset) {
        long start = Math.round(analysisPos);
        long ha = first ? 0 : start - prevStart;
        for (int c = 0; c < channels; c++) {
            for (int i = 0; i < N; i++) { re[i] = src.sample(c, start + i) * window[i]; im[i] = 0; }
            Fft.transform(re, im);
            int bins = N / 2 + 1;
            for (int k = 0; k < bins; k++) { mag[k] = Math.hypot(re[k], im[k]); phase[k] = Math.atan2(im[k], re[k]); }
            double[] pa = prevAnalysisPhase[c], ps = prevSynthPhase[c];
            if (first || ha <= 0) {
                System.arraycopy(phase, 0, synth, 0, bins);
            } else {
                // Peaks: local maxima over two neighbours on each side.
                for (int k = 0; k < bins; k++) {
                    double m = mag[k];
                    peak[k] = k >= 2 && k < bins - 2 && m > mag[k - 1] && m >= mag[k + 1] && m > mag[k - 2] && m >= mag[k + 2];
                }
                // Each bin belongs to the nearest peak (boundary at the lowest magnitude between two peaks).
                int lastPeak = -1;
                for (int k = 0; k < bins; k++) {
                    if (peak[k]) {
                        if (lastPeak >= 0) {
                            int low = lastPeak;
                            for (int j = lastPeak; j <= k; j++) if (mag[j] < mag[low]) low = j;
                            for (int j = lastPeak; j < low; j++) owner[j] = lastPeak;
                            for (int j = low; j < k; j++) owner[j] = k;
                        } else for (int j = 0; j < k; j++) owner[j] = k;
                        lastPeak = k;
                    }
                }
                if (lastPeak < 0) { for (int k = 0; k < bins; k++) owner[k] = -1; }
                else for (int j = lastPeak; j < bins; j++) owner[j] = lastPeak;
                // Peaks: true frequency from the phase advance, then advance by the synthesis hop.
                for (int k = 0; k < bins; k++) {
                    if (!peak[k]) continue;
                    double omega = TWO_PI * k / N;
                    double dev = princarg(phase[k] - pa[k] - omega * ha);
                    double freq = omega + dev / ha;
                    synth[k] = ps[k] + freq * HOP;
                }
                // Other bins keep their phase relation to their peak.
                for (int k = 0; k < bins; k++) {
                    if (peak[k]) continue;
                    int p = owner[k];
                    if (p < 0) { synth[k] = ps[k] + TWO_PI * k / N * HOP; continue; }
                    synth[k] = synth[p] + (phase[k] - phase[p]);
                }
            }
            for (int k = 0; k < bins; k++) { pa[k] = phase[k]; ps[k] = princarg(synth[k]); }
            // Back to time: the magnitudes with the new phases (conjugate-symmetric spectrum).
            for (int k = 0; k < bins; k++) { re[k] = mag[k] * Math.cos(synth[k]); im[k] = mag[k] * Math.sin(synth[k]); }
            for (int k = 1; k < N / 2; k++) { re[N - k] = re[k]; im[N - k] = -im[k]; }
            Fft.transform(re, im, true);
            double[] acc = accumulator[c];
            for (int i = 0; i < N; i++) acc[i] += re[i] * window[i];
            float[] o = out[c];
            for (int i = 0; i < HOP; i++) o[offset + i] = (float) (acc[i] / norm);
            System.arraycopy(acc, HOP, acc, 0, N - HOP);
            java.util.Arrays.fill(acc, N - HOP, N, 0);
        }
        prevStart = start;
        first = false;
    }
}
