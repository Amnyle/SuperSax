package app.supersax.domain.audio;

import app.supersax.domain.pitch.Tuning;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import org.jetbrains.annotations.Nullable;

/**
 * Pure hold analysis over a stream of {@link PitchFrame}s.
 *
 * <ul>
 *   <li>Segmentation: a hold starts when frames are voiced (confidence ≥ min confidence) for the onset
 *       time, and ends when they stop for the release gap.</li>
 *   <li>The attack and release trims are ignored.</li>
 *   <li>The reference is the target note if given, else the semitone nearest the median of the stable part.</li>
 * </ul>
 */
public final class HoldAnalysis {
    private HoldAnalysis() {}

    static boolean isSound(PitchFrame f, HoldConfig c) { return f.getFrequencyHz() != null && f.getConfidence() >= c.getMinConfidence(); }

    /**
     * Finds holds as {first frame index, last frame index} pairs. Gaps shorter than the release gap are
     * bridged (a hold survives a dropped frame).
     */
    public static List<int[]> segment(List<PitchFrame> frames, HoldConfig c) {
        List<int[]> holds = new ArrayList<>();
        int runStart = -1, lastSound = -1;
        boolean inHold = false;
        for (int i = 0; i < frames.size(); i++) {
            PitchFrame f = frames.get(i);
            if (isSound(f, c)) {
                if (runStart == -1) runStart = i;
                lastSound = i;
                if (!inHold && f.getTimeSec() - frames.get(runStart).getTimeSec() >= c.getOnsetSec()) inHold = true;
            } else if (lastSound >= 0 && f.getTimeSec() - frames.get(lastSound).getTimeSec() >= c.getReleaseGapSec()) {
                if (inHold) holds.add(new int[]{runStart, lastSound});
                inHold = false; runStart = -1; lastSound = -1;
            }
        }
        if (inHold) holds.add(new int[]{runStart, lastSound});
        return holds;
    }

    /** Analyses one hold (frames already segmented). Returns null if nothing stable remains after trimming. */
    public static @Nullable HoldReport analyse(List<PitchFrame> frames, @Nullable Integer targetMidi, HoldConfig c) {
        List<PitchFrame> sound = new ArrayList<>();
        for (PitchFrame f : frames) if (isSound(f, c)) sound.add(f);
        if (sound.isEmpty()) return null;
        double start = sound.get(0).getTimeSec();
        double end = sound.get(sound.size() - 1).getTimeSec();
        List<PitchFrame> stable = new ArrayList<>();
        for (PitchFrame f : sound) if (f.getTimeSec() >= start + c.getAttackTrimSec() && f.getTimeSec() <= end - c.getReleaseTrimSec()) stable.add(f);
        if (stable.size() < 2) return null;
        List<Double> midis = new ArrayList<>();
        for (PitchFrame f : stable) midis.add(Tuning.frequencyToMidi(f.getFrequencyHz(), c.getA4()));
        int ref;
        if (targetMidi != null) ref = targetMidi;
        else {
            List<Double> sorted = new ArrayList<>(midis);
            Collections.sort(sorted);
            ref = (int) Math.round(sorted.get(sorted.size() / 2));
        }
        double refHz = Tuning.midiToFrequency(ref, c.getA4());
        List<Double> cents = new ArrayList<>();
        List<Double> times = new ArrayList<>();
        for (PitchFrame f : stable) { cents.add(Tuning.cents(f.getFrequencyHz(), refHz)); times.add(f.getTimeSec()); }
        double mean = average(cents);
        double t0 = times.get(0), t1 = times.get(times.size() - 1);
        double stableSec = t1 - t0;
        double window = Math.min(0.5, stableSec / 3);
        List<Double> head = new ArrayList<>(), tail = new ArrayList<>();
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        int inTune = 0;
        double tol = c.getToleranceCents();
        for (int i = 0; i < cents.size(); i++) {
            double v = cents.get(i);
            if (times.get(i) <= t0 + window) head.add(v);
            if (times.get(i) >= t1 - window) tail.add(v);
            min = Math.min(min, v); max = Math.max(max, v);
            if (Math.abs(v) <= tol) inTune++;
        }
        PitchDirection dir = mean > tol / 2 ? PitchDirection.SHARP : mean < -tol / 2 ? PitchDirection.FLAT : PitchDirection.CENTERED;
        return new HoldReport(start, end, ref, stableSec, inTune / (double) cents.size(), tol, mean, average(tail) - average(head),
            linearSlope(times, cents), min, max, dir, downsample(times, cents, c.getTraceHz()), c.getTraceHz());
    }

    public static @Nullable HoldReport analyse(List<PitchFrame> frames, @Nullable Integer targetMidi) { return analyse(frames, targetMidi, new HoldConfig()); }

    public static @Nullable HoldReport analyse(List<PitchFrame> frames) { return analyse(frames, null, new HoldConfig()); }

    /** Segments the frames and analyses every hold found. */
    public static List<HoldReport> analyseAll(List<PitchFrame> frames, @Nullable Integer targetMidi, HoldConfig c) {
        List<HoldReport> out = new ArrayList<>();
        for (int[] r : segment(frames, c)) {
            HoldReport h = analyse(frames.subList(r[0], r[1] + 1), targetMidi, c);
            if (h != null) out.add(h);
        }
        return out;
    }

    public static List<HoldReport> analyseAll(List<PitchFrame> frames, @Nullable Integer targetMidi) { return analyseAll(frames, targetMidi, new HoldConfig()); }

    public static List<HoldReport> analyseAll(List<PitchFrame> frames) { return analyseAll(frames, null, new HoldConfig()); }

    /** Least-squares slope (y per x unit). */
    public static double linearSlope(List<Double> x, List<Double> y) {
        double mx = average(x), my = average(y), num = 0, den = 0;
        for (int i = 0; i < x.size(); i++) { num += (x.get(i) - mx) * (y.get(i) - my); den += (x.get(i) - mx) * (x.get(i) - mx); }
        return den == 0.0 ? 0.0 : num / den;
    }

    /** Averages values into bins of 1/hz seconds. */
    public static List<Float> downsample(List<Double> times, List<Double> values, double hz) {
        List<Float> out = new ArrayList<>();
        if (times.isEmpty()) return out;
        double t0 = times.get(0);
        TreeMap<Integer, double[]> bins = new TreeMap<>(); // bin -> {sum, count}
        for (int i = 0; i < values.size(); i++) {
            double[] b = bins.computeIfAbsent((int) ((times.get(i) - t0) * hz), k -> new double[2]);
            b[0] += values.get(i); b[1]++;
        }
        for (double[] b : bins.values()) out.add((float) (b[0] / b[1]));
        return out;
    }

    static double average(List<Double> v) {
        if (v.isEmpty()) return Double.NaN;
        double s = 0;
        for (double d : v) s += d;
        return s / v.size();
    }
}
