package app.supersax.domain.audio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Streams audio into overlapping YIN windows (hop {@code hop}) and smooths the voiced frequencies with a
 * running median over {@code medianFrames} frames (in the log-frequency domain), which removes
 * single-frame glitches without lagging much (5 × 11.6 ms).
 */
public final class PitchTracker {
    private final Yin yin;
    private final int hop;
    private final int medianFrames;
    private final float[] buffer;
    private int filled;
    private long samplesSeen;
    private int pendingHop;
    private final ArrayDeque<Double> recent = new ArrayDeque<>();

    public PitchTracker(Yin yin, int hop, int medianFrames) {
        this.yin = yin;
        this.hop = hop;
        this.medianFrames = medianFrames;
        this.buffer = new float[yin.getWindowSize()];
    }

    public PitchTracker(Yin yin) { this(yin, 512, 5); }

    public PitchTracker() { this(new Yin()); }

    public int getHop() { return hop; }

    /** Feeds samples; returns one frame per completed hop (the window slides as samples arrive). */
    public List<PitchFrame> push(float[] samples, int count) {
        List<PitchFrame> out = new ArrayList<>();
        int w = yin.getWindowSize();
        int i = 0;
        while (i < count) {
            if (filled < w) {
                int take = Math.min(w - filled, count - i);
                System.arraycopy(samples, i, buffer, filled, take);
                filled += take; i += take; samplesSeen += take;
                if (filled == w) out.add(analyse());
            } else {
                int take = Math.min(hop - pendingHop, count - i);
                System.arraycopy(buffer, take, buffer, 0, w - take);
                System.arraycopy(samples, i, buffer, w - take, take);
                i += take; samplesSeen += take; pendingHop += take;
                if (pendingHop == hop) { pendingHop = 0; out.add(analyse()); }
            }
        }
        return out;
    }

    public List<PitchFrame> push(float[] samples) { return push(samples, samples.length); }

    private PitchFrame analyse() {
        Yin.Result r = yin.detect(buffer);
        double time = (double) (samplesSeen - yin.getWindowSize() / 2) / yin.getSampleRate();
        Double f = r.getFrequencyHz();
        Double smoothed = null;
        if (f != null) {
            recent.addLast(Math.log(f));
            while (recent.size() > medianFrames) recent.removeFirst();
            Double[] sorted = recent.toArray(new Double[0]);
            Arrays.sort(sorted);
            smoothed = Math.exp(sorted[sorted.length / 2]);
        } else {
            recent.clear();
        }
        return new PitchFrame(time, smoothed, r.getConfidence(), r.getRms());
    }

    public void reset() { filled = 0; samplesSeen = 0; pendingHop = 0; recent.clear(); }
}
