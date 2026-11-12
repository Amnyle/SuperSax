package app.supersax.domain.drills;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Rhythm reading: one 4/4 bar on one note; after a one-bar count-in the player presses for each note and holds
 * it for its length (released at its end). Bars are built
 * from rhythm cells (in sixteenths; negative = rest). Levels: 1 quarters, halves, whole; 2 adds quarter
 * rests; 3 adds eighth pairs; 4 adds dotted quarter + eighth; 5 adds syncopation; 6 adds sixteenths;
 * 7 swing eighths (written as eighths, played long-short). Taps are judged against the beat after
 * removing the constant delay of the phone (the median offset), so audio latency does not count.
 */
public final class RhythmDrill {
    private RhythmDrill() {}

    public static final int LEVELS = 7;
    public static final int ROUND = 8;

    private static final List<int[]> L1 = Arrays.asList(new int[]{4}, new int[]{8}, new int[]{4}, new int[]{8}, new int[]{16});
    private static final List<int[]> L2 = Arrays.asList(new int[]{-4}, new int[]{4});
    private static final List<int[]> L3 = Arrays.asList(new int[]{2, 2}, new int[]{2, 2});
    private static final List<int[]> L4 = Collections.singletonList(new int[]{6, 2});
    private static final List<int[]> L5 = Arrays.asList(new int[]{2, 4, 2}, new int[]{-2, 2});
    private static final List<int[]> L6 = Arrays.asList(new int[]{1, 1, 1, 1}, new int[]{2, 1, 1}, new int[]{1, 1, 2});

    public static List<int[]> cells(int level) {
        List<int[]> out = new ArrayList<>(L1);
        if (level >= 2) out.addAll(L2);
        if (level >= 3) out.addAll(L3);
        if (level >= 4) out.addAll(L4);
        if (level >= 5) out.addAll(L5);
        if (level >= 6 && level != 7) out.addAll(L6);
        return out;
    }

    public static int bpm(int level) { return level >= 6 && level != 7 ? 60 : 72; }

    public static boolean swing(int level) { return level == 7; }

    /** One bar: durations in sixteenths, negative for rests, summing to 16, and starting on a note. */
    public static final class Question {
        private final List<Integer> durations;
        private final boolean swing;
        Question(List<Integer> durations, boolean swing) { this.durations = durations; this.swing = swing; }
        public List<Integer> getDurations() { return durations; }
        public boolean getSwing() { return swing; }

        /** Note start times in sixteenths (rests skipped). */
        public List<Double> onsets() {
            List<Double> out = new ArrayList<>();
            int t = 0;
            for (int d : durations) {
                if (d > 0) {
                    double pos = t;
                    // Swing: an eighth starting on the "and" sounds two thirds into the beat.
                    if (swing && t % 4 == 2) pos = t - 2 + 4.0 * 2 / 3;
                    out.add(pos);
                }
                t += Math.abs(d);
            }
            return out;
        }

        /** Sixteenth position → beats, with swing (an off-beat eighth sounds two thirds into the beat). */
        private double beats(int sixteenths) {
            double pos = sixteenths;
            if (swing && sixteenths % 4 == 2) pos = sixteenths - 2 + 4.0 * 2 / 3;
            return pos / 4.0;
        }

        /** Each note as {start, end} in beats from the start of the bar (rests skipped). */
        public List<double[]> spans() {
            List<double[]> out = new ArrayList<>();
            int t = 0;
            for (int d : durations) {
                int len = Math.abs(d);
                if (d > 0) out.add(new double[]{beats(t), beats(t + len)});
                t += len;
            }
            return out;
        }

        /** The bar in the score format ("B4:1/4 r:1/8 …"). */
        public String measure() {
            StringBuilder b = new StringBuilder();
            for (int d : durations) {
                if (b.length() > 0) b.append(' ');
                b.append(d > 0 ? "B4" : "r").append(':').append(fraction(Math.abs(d)));
            }
            return b.toString();
        }

        private static String fraction(int sixteenths) {
            int n = sixteenths, d = 16;
            while (n % 2 == 0 && d > 1) { n /= 2; d /= 2; }
            return n + "/" + d;
        }
    }

    public static List<Question> round(int level, int count, long seed) {
        Random rnd = new Random(seed);
        List<int[]> cells = cells(level);
        List<Question> out = new ArrayList<>();
        while (out.size() < count) {
            List<Integer> bar = new ArrayList<>();
            int t = 0;
            int guard = 0;
            while (t < 16 && guard++ < 100) {
                int[] cell = cells.get(rnd.nextInt(cells.size()));
                int len = 0;
                for (int d : cell) len += Math.abs(d);
                // Cells stay inside a half bar unless they start on beat 1 or 3 (readable bars).
                if (t + len > 16) continue;
                if (len > 4 && t % 8 != 0 && len != 6) continue;
                if (len == 6 && t % 8 != 0) continue;
                if (bar.isEmpty() && cell[0] < 0) continue;
                for (int d : cell) bar.add(d);
                t += len;
            }
            if (t == 16 && !bar.isEmpty() && bar.get(0) > 0) out.add(new Question(bar, swing(level)));
        }
        return out;
    }

    public static List<Question> round(int level, long seed) { return round(level, ROUND, seed); }

    /** Timing tolerance in milliseconds at a level (tighter as the rhythms get finer). */
    public static double toleranceMs(int level) { return level >= 6 ? 75 : 100; }

    /** Result of a tapped bar: right when every note was tapped once in time and nothing else was tapped. */
    public static final class Result {
        public final boolean correct;
        public final int missed, extra;
        public final double meanErrorMs;
        Result(boolean correct, int missed, int extra, double meanErrorMs) { this.correct = correct; this.missed = missed; this.extra = extra; this.meanErrorMs = meanErrorMs; }
        public boolean getCorrect() { return correct; }
        public int getMissed() { return missed; }
        public int getExtra() { return extra; }
        public double getMeanErrorMs() { return meanErrorMs; }
    }

    /**
     * Judges taps (ms from the expected start of the bar) against the expected onsets (ms). The median
     * difference between each tap and its nearest onset is the phone's delay and is removed first.
     */
    public static Result judge(List<Double> expectedMs, List<Double> tapsMs, double toleranceMs) {
        if (tapsMs.isEmpty()) return new Result(false, expectedMs.size(), 0, 0);
        List<Double> diffs = new ArrayList<>();
        for (double tap : tapsMs) {
            double best = Double.MAX_VALUE;
            for (double e : expectedMs) if (Math.abs(tap - e) < Math.abs(best)) best = tap - e;
            diffs.add(best);
        }
        List<Double> sorted = new ArrayList<>(diffs);
        Collections.sort(sorted);
        double offset = sorted.get(sorted.size() / 2);
        if (Math.abs(offset) > 250) offset = 0;
        boolean[] used = new boolean[tapsMs.size()];
        int missed = 0;
        double errSum = 0;
        int matched = 0;
        for (double e : expectedMs) {
            int bestI = -1;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < tapsMs.size(); i++) {
                if (used[i]) continue;
                double d = Math.abs(tapsMs.get(i) - offset - e);
                if (d < best) { best = d; bestI = i; }
            }
            if (bestI >= 0 && best <= toleranceMs) { used[bestI] = true; errSum += best; matched++; }
            else missed++;
        }
        int extra = 0;
        for (boolean u : used) if (!u) extra++;
        return new Result(missed == 0 && extra == 0, missed, extra, matched == 0 ? 0 : errSum / matched);
    }

    /** How one note was played. */
    public enum Verdict { OK, EARLY, LATE, SHORT, LONG, MISSED }

    /** Result of a held bar: one verdict per note, presses that matched no note, and whether it was right. */
    public static final class HoldResult {
        public final List<Verdict> verdicts;
        public final int extra;
        public final double offsetMs;
        HoldResult(List<Verdict> verdicts, int extra, double offsetMs) { this.verdicts = verdicts; this.extra = extra; this.offsetMs = offsetMs; }
        public List<Verdict> getVerdicts() { return verdicts; }
        public int getExtra() { return extra; }
        public double getOffsetMs() { return offsetMs; }
        public boolean getCorrect() {
            if (extra > 0) return false;
            for (Verdict v : verdicts) if (v != Verdict.OK) return false;
            return true;
        }
        /** The first note not played right (−1 if none). */
        public int getFirstWrong() {
            for (int i = 0; i < verdicts.size(); i++) if (verdicts.get(i) != Verdict.OK) return i;
            return -1;
        }
    }

    /**
     * Judges presses ({down, up} in ms from beat 1 of the bar) against note spans ({start, end} in ms). The
     * phone's constant delay (median offset of the presses, at most 250 ms) is removed first. A press must start
     * within [toleranceMs] of its note and end near the note's end: not earlier than max(150 ms, 35% of the note)
     * before it, nor later than max(150 ms, 25% of the note) after it. Very short touches (under 40 ms) are ignored.
     */
    public static HoldResult judgeHolds(List<double[]> spansMs, List<double[]> pressesMs, double toleranceMs, double beatMs) {
        List<double[]> presses = new ArrayList<>();
        for (double[] p : pressesMs) if (p[1] - p[0] >= 40) presses.add(p);
        double offset = 0;
        if (!presses.isEmpty()) {
            List<Double> diffs = new ArrayList<>();
            for (double[] p : presses) {
                double best = Double.MAX_VALUE;
                for (double[] s : spansMs) if (Math.abs(p[0] - s[0]) < Math.abs(best)) best = p[0] - s[0];
                diffs.add(best);
            }
            Collections.sort(diffs);
            offset = diffs.get(diffs.size() / 2);
            if (Math.abs(offset) > 250) offset = 0;
        }
        boolean[] used = new boolean[presses.size()];
        List<Verdict> verdicts = new ArrayList<>();
        for (double[] s : spansMs) {
            int bestI = -1;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < presses.size(); i++) {
                if (used[i]) continue;
                double d = Math.abs(presses.get(i)[0] - offset - s[0]);
                if (d < best) { best = d; bestI = i; }
            }
            double window = Math.max(toleranceMs * 2, beatMs * 0.45);
            if (bestI < 0 || best > window) { verdicts.add(Verdict.MISSED); continue; }
            used[bestI] = true;
            double[] p = presses.get(bestI);
            double startErr = p[0] - offset - s[0];
            double dur = s[1] - s[0];
            double endErr = p[1] - offset - s[1];
            if (startErr < -toleranceMs) verdicts.add(Verdict.EARLY);
            else if (startErr > toleranceMs) verdicts.add(Verdict.LATE);
            else if (endErr < -Math.max(150, 0.35 * dur)) verdicts.add(Verdict.SHORT);
            else if (endErr > Math.max(150, 0.25 * dur)) verdicts.add(Verdict.LONG);
            else verdicts.add(Verdict.OK);
        }
        int extra = 0;
        for (boolean u : used) if (!u) extra++;
        return new HoldResult(verdicts, extra, offset);
    }
}
