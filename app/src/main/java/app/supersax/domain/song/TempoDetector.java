package app.supersax.domain.song;

import app.supersax.domain.audio.Fft;

/**
 * Tempo and first beat of a recording (mono samples). Onset strength is the spectral flux of log
 * magnitudes (1024-sample frames, hop 512); the tempo is the autocorrelation peak of that envelope
 * between 60 and 200 BPM, weighted by a log-normal prior centred on 120 BPM; tempo and first beat are
 * then refined together. Half or double tempo stays possible for very fast or slow songs (the player
 * has ×2 and ÷2 buttons).
 */
public final class TempoDetector {
    private TempoDetector() {}

    public static final int FRAME = 1024, HOP = 512;
    public static final double MIN_BPM = 60, MAX_BPM = 200;

    /** Detected tempo, the time of the first beat, and a 0..1 confidence. */
    public static final class Result {
        private final double bpm, firstBeatSec, confidence;
        public Result(double bpm, double firstBeatSec, double confidence) { this.bpm = bpm; this.firstBeatSec = firstBeatSec; this.confidence = confidence; }
        public double getBpm() { return bpm; }
        public double getFirstBeatSec() { return firstBeatSec; }
        public double getConfidence() { return confidence; }
    }

    /** Onset strength envelope, one value per hop, mean-removed and half-wave rectified. */
    public static double[] onsetEnvelope(float[] x, int sampleRate) {
        int frames = Math.max(0, (x.length - FRAME) / HOP + 1);
        double[] env = new double[frames];
        double[] window = new double[FRAME];
        for (int i = 0; i < FRAME; i++) window[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / FRAME);
        double[] prev = new double[FRAME / 2];
        double[] re = new double[FRAME], im = new double[FRAME];
        for (int f = 0; f < frames; f++) {
            int off = f * HOP;
            for (int i = 0; i < FRAME; i++) { re[i] = x[off + i] * window[i]; im[i] = 0; }
            Fft.transform(re, im);
            double flux = 0;
            for (int k = 1; k < FRAME / 2; k++) {
                double mag = Math.log1p(1000 * Math.hypot(re[k], im[k]));
                double d = mag - prev[k];
                if (d > 0 && f > 0) flux += d;
                prev[k] = mag;
            }
            env[f] = flux;
        }
        // Remove the slow trend (about 0.5 s moving average) and keep the rises.
        double fps = (double) sampleRate / HOP;
        int w = Math.max(1, (int) Math.round(fps * 0.25));
        double[] out = new double[frames];
        double sum = 0;
        int count = 0;
        for (int i = 0; i < frames + w; i++) {
            if (i < frames) { sum += env[i]; count++; }
            if (i - 2 * w - 1 >= 0) { sum -= env[i - 2 * w - 1]; count--; }
            int c = i - w;
            if (c >= 0 && c < frames) out[c] = Math.max(0, env[c] - sum / count);
        }
        return out;
    }

    /**
     * When the music starts: the first 1024-sample frame louder than 35 dB below the loud part of the
     * recording (the 95th percentile of frame levels), so a quiet intro still counts but silence does not.
     */
    public static double soundStartSec(float[] x, int sampleRate) {
        int frames = x.length / FRAME;
        if (frames == 0) return 0;
        double[] rms = new double[frames];
        for (int f = 0; f < frames; f++) {
            double s = 0;
            for (int i = f * FRAME; i < (f + 1) * FRAME; i++) s += x[i] * x[i];
            rms[f] = Math.sqrt(s / FRAME);
        }
        double[] sorted = rms.clone();
        java.util.Arrays.sort(sorted);
        double loud = sorted[Math.min(frames - 1, (int) (frames * 0.95))];
        double threshold = loud * Math.pow(10, -35 / 20.0);
        for (int f = 0; f < frames; f++) if (rms[f] > threshold && rms[f] > 1e-4) return (double) f * FRAME / sampleRate;
        return 0;
    }

    /** [1 2 1]/4 smoothing: beat peaks between two frames still line up at whole-frame lags. */
    static double[] smooth(double[] e) {
        double[] o = new double[e.length];
        for (int i = 0; i < e.length; i++) {
            double l = i > 0 ? e[i - 1] : e[i], r = i + 1 < e.length ? e[i + 1] : e[i];
            o[i] = 0.25 * l + 0.5 * e[i] + 0.25 * r;
        }
        return o;
    }

    public static Result detect(float[] x, int sampleRate) {
        double fps = (double) sampleRate / HOP;
        double[] env = smooth(smooth(onsetEnvelope(x, sampleRate)));
        int n = env.length;
        int minLag = (int) Math.floor(60 * fps / MAX_BPM), maxLag = (int) Math.ceil(60 * fps / MIN_BPM);
        if (n < maxLag * 4) return new Result(120, 0, 0);
        double[] ac = new double[maxLag + 2];
        for (int lag = minLag - 1; lag <= maxLag + 1; lag++) {
            if (lag <= 0) continue;
            double s = 0;
            for (int i = 0; i + lag < n; i++) s += env[i] * env[i + lag];
            ac[lag] = s / (n - lag);
        }
        int best = -1;
        double bestScore = -1, total = 0;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double bpm = 60 * fps / lag;
            double prior = Math.exp(-0.5 * Math.pow(Math.log(bpm / 120.0) / Math.log(2) / 0.9, 2));
            double score = ac[lag] * prior;
            total += score;
            if (score > bestScore) { bestScore = score; best = lag; }
        }
        // Parabolic refinement of the peak lag.
        double lag = best;
        double a = ac[best - 1], b = ac[best], c = ac[best + 1];
        double denom = a - 2 * b + c;
        if (denom < 0) lag = best + 0.5 * (a - c) / denom;
        // Tempo and beat phase together: a fine grid search around the autocorrelation peak for the
        // period and offset whose beat grid gathers the most onset strength (a tempo off by a fraction
        // of a frame would drift by several frames over a whole song).
        double bestLag = lag, bestSum = -1;
        int bestOffset = 0;
        for (double p = lag - 0.6; p <= lag + 0.6; p += 0.01) {
            if (p < minLag - 1) continue;
            for (int off = 0; off < (int) Math.ceil(p); off++) {
                double s = 0;
                for (double t = off; t < n; t += p) s += env[(int) Math.round(t) < n ? (int) Math.round(t) : n - 1];
                if (s > bestSum) { bestSum = s; bestOffset = off; bestLag = p; }
            }
        }
        double bpm = 60 * fps / bestLag;
        double confidence = total <= 0 ? 0 : Math.min(1, bestScore * (maxLag - minLag + 1) / total / 4);
        // Frame centre time: a frame starting at f*HOP is centred FRAME/2 samples later.
        double firstBeat = (bestOffset * HOP + FRAME / 2.0) / sampleRate;
        // Bar 1 starts with the music, not in the silence before it: move to the first beat of the grid
        // at or after the moment sound starts (30 ms tolerance for the attack).
        double period = bestLag * HOP / sampleRate;
        double sound = soundStartSec(x, sampleRate);
        if (sound - 0.03 > firstBeat) firstBeat += Math.ceil((sound - 0.03 - firstBeat) / period) * period;
        return new Result(bpm, firstBeat, confidence);
    }
}
