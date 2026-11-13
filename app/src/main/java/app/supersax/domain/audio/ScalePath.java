package app.supersax.domain.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import org.jetbrains.annotations.Nullable;

/**
 * Checks a played scale run as a path: it starts on the root, every note is a neighbouring note of the scale (one
 * step up or down, as often as wanted: do ré mi fa sol la si do si la sol fa mi ré do si do is fine), it reaches the
 * octave at least once, and it ends on the very note it started on. One stray note between two neighbours (a squeak
 * the microphone caught) is forgiven. The tempo is read from the time between notes (one note per beat).
 */
public final class ScalePath {
    private ScalePath() {}

    public enum Problem { NONE, TOO_SHORT, WRONG_START, NOT_IN_SCALE, NOT_NEIGHBOUR, NO_OCTAVE, NOT_HOME }

    public static final class Result {
        public final Problem problem;
        /** Index (in the notes played) of the problem, −1 when none. */
        public final int index;
        /** The note played there and, for a jump, the note before it (concert MIDI, −1 when not relevant). */
        public final int played, before;
        public final int notes;
        public final @Nullable Double bpm;
        Result(Problem problem, int index, int played, int before, int notes, @Nullable Double bpm) {
            this.problem = problem; this.index = index; this.played = played; this.before = before; this.notes = notes; this.bpm = bpm;
        }
        public Problem getProblem() { return problem; }
        public int getIndex() { return index; }
        public int getPlayed() { return played; }
        public int getBefore() { return before; }
        public int getNotes() { return notes; }
        public @Nullable Double getBpm() { return bpm; }
        public boolean getClean() { return problem == Problem.NONE; }
    }

    /** [scale]: one octave of the scale going up (concert MIDI, root first; the top octave may be included). */
    public static Result check(List<DetectedNote> played, List<Integer> scale) {
        // Merge repeated notes (a long note can be cut in two by the detector).
        List<DetectedNote> seq = new ArrayList<>();
        for (DetectedNote n : played) if (seq.isEmpty() || seq.get(seq.size() - 1).getMidi() != n.getMidi()) seq.add(n);
        Double bpm = tempo(seq);
        if (seq.size() < 4) return new Result(Problem.TOO_SHORT, -1, -1, -1, seq.size(), bpm);
        boolean[] inScale = new boolean[12];
        for (int m : scale) inScale[Math.floorMod(m, 12)] = true;
        int rootPc = Math.floorMod(scale.get(0), 12);
        int start = seq.get(0).getMidi();
        if (Math.floorMod(start, 12) != rootPc) return new Result(Problem.WRONG_START, 0, start, -1, seq.size(), bpm);
        // The scale as a ladder over the whole range played.
        TreeSet<Integer> ladder = new TreeSet<>();
        for (int m = start - 30; m <= start + 30; m++) if (inScale[Math.floorMod(m, 12)]) ladder.add(m);
        List<Integer> path = new ArrayList<>();
        List<Integer> where = new ArrayList<>();
        boolean forgiven = false;
        for (int i = 0; i < seq.size(); i++) {
            int m = seq.get(i).getMidi();
            if (!path.isEmpty() && !neighbours(ladder, path.get(path.size() - 1), m)) {
                // A stray note between two neighbours is forgiven once.
                boolean nextFits = i + 1 < seq.size() && neighbours(ladder, path.get(path.size() - 1), seq.get(i + 1).getMidi());
                if (!forgiven && nextFits) { forgiven = true; continue; }
                Problem p = inScale[Math.floorMod(m, 12)] ? Problem.NOT_NEIGHBOUR : Problem.NOT_IN_SCALE;
                return new Result(p, i, m, path.get(path.size() - 1), seq.size(), bpm);
            }
            if (path.isEmpty() && !inScale[Math.floorMod(m, 12)]) return new Result(Problem.NOT_IN_SCALE, i, m, -1, seq.size(), bpm);
            path.add(m);
            where.add(i);
        }
        int last = path.get(path.size() - 1);
        if (!path.contains(start + 12) && !path.contains(start - 12)) return new Result(Problem.NO_OCTAVE, -1, start, -1, seq.size(), bpm);
        if (last != start) return new Result(Problem.NOT_HOME, where.get(where.size() - 1), last, start, seq.size(), bpm);
        return new Result(Problem.NONE, -1, -1, -1, seq.size(), bpm);
    }

    private static boolean neighbours(TreeSet<Integer> ladder, int a, int b) {
        if (!ladder.contains(a) || !ladder.contains(b) || a == b) return false;
        Integer up = ladder.higher(a), down = ladder.lower(a);
        return (up != null && up == b) || (down != null && down == b);
    }

    /** Beats per minute from the median time between note starts (one note per beat), or null. */
    static @Nullable Double tempo(List<DetectedNote> seq) {
        if (seq.size() < 4) return null;
        double[] gaps = new double[seq.size() - 1];
        for (int i = 1; i < seq.size(); i++) gaps[i - 1] = seq.get(i).getStartSec() - seq.get(i - 1).getStartSec();
        Arrays.sort(gaps);
        double g = gaps[gaps.length / 2];
        if (g <= 0.05) return null;
        double bpm = 60.0 / g;
        return bpm < 30 || bpm > 320 ? null : bpm;
    }
}
