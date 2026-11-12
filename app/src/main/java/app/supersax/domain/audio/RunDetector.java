package app.supersax.domain.audio;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Listens to a stream of pitch frames and cuts it into scale runs: a run starts with sound and ends after
 * {@link #SILENCE_SEC} of silence once at least {@link #MIN_NOTES} notes were heard. Each finished run is
 * checked against the expected scale (up and down), lined up with the octave the player chose.
 */
public final class RunDetector {
    public static final double SILENCE_SEC = 0.7;
    public static final int MIN_NOTES = 4;

    private final ScaleChecker checker;
    private final List<Integer> expected;
    private final List<PitchFrame> frames = new ArrayList<>();
    private double lastVoicedSec = -1;

    public RunDetector(List<Integer> expectedConcertMidi, double a4) {
        this.expected = expectedConcertMidi;
        this.checker = new ScaleChecker(a4);
    }

    /** A checked run, and whether it was clean (every note right, at most one extra note). */
    public static final class Run {
        private final ScaleCheck check;
        private final List<Integer> expected;
        Run(ScaleCheck check, List<Integer> expected) { this.check = check; this.expected = expected; }
        public ScaleCheck getCheck() { return check; }
        public List<Integer> getExpected() { return expected; }
        public boolean getClean() { return check.getWrong() == 0 && check.getMissing() == 0 && check.getExtra() <= 1; }
        /** First wrong note: index into the expected notes and the note played instead (null when none). */
        public @Nullable int[] firstMistake() {
            List<DetectedNote> played = check.getPlayed();
            for (int i = 0; i < Math.min(played.size(), expected.size()); i++) {
                if (played.get(i).getMidi() != expected.get(i)) return new int[]{i, played.get(i).getMidi()};
            }
            if (played.size() < expected.size()) return new int[]{played.size(), -1};
            return null;
        }
    }

    /** Feeds one frame; returns a run when one has just ended. */
    public @Nullable Run feed(PitchFrame f) {
        if (f.getVoiced()) {
            frames.add(f);
            lastVoicedSec = f.getTimeSec();
            return null;
        }
        if (frames.isEmpty()) return null;
        frames.add(f);
        if (f.getTimeSec() - lastVoicedSec < SILENCE_SEC) return null;
        List<PitchFrame> run = new ArrayList<>(frames);
        frames.clear();
        List<DetectedNote> notes = checker.segment(run);
        if (notes.size() < MIN_NOTES) return null;
        List<Integer> aligned = alignOctave(expected, notes.get(0).getMidi());
        return new Run(checker.check(run, aligned), aligned);
    }

    /** The expected notes moved by whole octaves so they start nearest to the first note played. */
    public static List<Integer> alignOctave(List<Integer> expected, int firstPlayed) {
        int shift = Math.round((firstPlayed - expected.get(0)) / 12f) * 12;
        List<Integer> out = new ArrayList<>();
        for (int m : expected) out.add(m + shift);
        return out;
    }

    public void reset() { frames.clear(); lastVoicedSec = -1; }
}
