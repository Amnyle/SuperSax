package app.supersax.domain.longtone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Which note to hold next in the long-tone coach. Every note of the player's range keeps a chance, but the notes
 * held worst lately (short, or out of tune) come up more often: a priority index, not a fixed order. Notes are
 * written MIDI (what the player reads and fingers).
 */
public final class LongTonePlan {
    /** A past hold of [writtenMidi] (time, seconds held, share in tune). */
    public static final class Hold {
        public final int writtenMidi;
        public final long atMs;
        public final double stableSec, inTuneShare;
        public Hold(int writtenMidi, long atMs, double stableSec, double inTuneShare) {
            this.writtenMidi = writtenMidi; this.atMs = atMs; this.stableSec = stableSec; this.inTuneShare = inTuneShare;
        }
    }

    public enum Reason { NEW, WORK_ON, REVIEW }

    public static final class Pick {
        public final int writtenMidi;
        public final double struggle;
        public final Reason reason;
        Pick(int writtenMidi, double struggle, Reason reason) { this.writtenMidi = writtenMidi; this.struggle = struggle; this.reason = reason; }
    }

    /** Seconds of a good long tone; holding this long fully in tune scores 1. */
    public static final double GOAL_SEC = 8;
    private static final int RECENT = 8;
    private static final int[] NATURALS = {0, 2, 4, 5, 7, 9, 11};

    private LongTonePlan() { }

    /**
     * The notes to practise: natural notes from [low] to [high] (the player's range; a beginner's middle range when
     * unknown), plus sharps and flats already held there.
     */
    public static List<Integer> candidates(int low, int high, Collection<Hold> holds) {
        TreeSet<Integer> out = new TreeSet<>();
        for (int m = low; m <= high; m++) {
            int pc = Math.floorMod(m, 12);
            for (int n : NATURALS) if (n == pc) out.add(m);
        }
        for (Hold h : holds) if (h.writtenMidi >= low && h.writtenMidi <= high) out.add(h.writtenMidi);
        return new ArrayList<>(out);
    }

    /** 0 (held well lately) to 1 (struggling); -1 when never held. Newer holds count more. */
    public static double struggle(int writtenMidi, Collection<Hold> holds) {
        List<Hold> mine = new ArrayList<>();
        for (Hold h : holds) if (h.writtenMidi == writtenMidi) mine.add(h);
        if (mine.isEmpty()) return -1;
        mine.sort((a, b) -> Long.compare(b.atMs, a.atMs));
        double sum = 0, wsum = 0, w = 1;
        for (int i = 0; i < Math.min(RECENT, mine.size()); i++) {
            Hold h = mine.get(i);
            double q = 0.6 * Math.max(0, Math.min(1, h.inTuneShare)) + 0.4 * Math.min(1, h.stableSec / GOAL_SEC);
            sum += q * w; wsum += w; w *= 0.8;
        }
        return 1 - sum / wsum;
    }

    /** Chance weight: a note never held or held badly comes up up to five times as often as one held well. */
    public static double weight(double struggle) { return struggle < 0 ? 1.2 : 0.4 + 1.6 * struggle; }

    /** Struggle of every candidate, for the screen's priority list. */
    public static Map<Integer, Double> priorities(List<Integer> candidates, Collection<Hold> holds) {
        Map<Integer, Double> out = new TreeMap<>();
        for (int m : candidates) out.put(m, struggle(m, holds));
        return out;
    }

    /** The next note: weighted at random, never one of the [recent] picks while there are other notes. */
    public static Pick next(List<Integer> candidates, Collection<Hold> holds, List<Integer> recent, Random rnd) {
        List<Integer> pool = new ArrayList<>();
        for (int m : candidates) if (!recent.contains(m)) pool.add(m);
        if (pool.isEmpty()) pool.addAll(candidates);
        if (pool.isEmpty()) throw new IllegalArgumentException("no notes");
        double[] w = new double[pool.size()];
        double total = 0;
        for (int i = 0; i < w.length; i++) { w[i] = weight(struggle(pool.get(i), holds)); total += w[i]; }
        double r = rnd.nextDouble() * total;
        int at = w.length - 1;
        for (int i = 0; i < w.length; i++) { r -= w[i]; if (r < 0) { at = i; break; } }
        int m = pool.get(at);
        double s = struggle(m, holds);
        return new Pick(m, s, s < 0 ? Reason.NEW : s >= 0.35 ? Reason.WORK_ON : Reason.REVIEW);
    }
}
