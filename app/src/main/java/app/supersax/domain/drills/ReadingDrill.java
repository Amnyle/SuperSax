package app.supersax.domain.drills;

import app.supersax.domain.pitch.Letter;
import app.supersax.domain.pitch.PitchClass;
import app.supersax.domain.pitch.SpelledPitch;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Note-reading drill: a written note on the treble staff, answered with a note-name keypad and an
 * accidental toggle. Levels widen the range, then add accidentals: 1 staff only (E4–F5), 2 + one ledger
 * line each side (C4–A5), 3 the whole alto range (B♭3–F♯6), 4 + sharps and flats in the middle,
 * 5 everything with accidentals.
 */
public final class ReadingDrill {
    /** Number of levels (range first, then accidentals). */
    public static final int LEVELS = DrillRules.MAX_LEVEL;
    private ReadingDrill() {}

    public static final class Level {
        private final int lowWrittenMidi, highWrittenMidi;
        private final boolean accidentals;
        public Level(int lowWrittenMidi, int highWrittenMidi, boolean accidentals) {
            this.lowWrittenMidi = lowWrittenMidi; this.highWrittenMidi = highWrittenMidi; this.accidentals = accidentals;
        }
        public int getLowWrittenMidi() { return lowWrittenMidi; }
        public int getHighWrittenMidi() { return highWrittenMidi; }
        public boolean getAccidentals() { return accidentals; }
    }

    public static Level level(int n) {
        switch (Math.max(1, Math.min(DrillRules.MAX_LEVEL, n))) {
            case 1: return new Level(64, 77, false);
            case 2: return new Level(60, 81, false);
            case 3: return new Level(58, 90, false);
            case 4: return new Level(60, 81, true);
            default: return new Level(58, 90, true);
        }
    }

    /** All spelled candidates in the level's range (naturals, plus single sharps and flats if allowed). */
    public static List<SpelledPitch> candidates(Level level) {
        List<SpelledPitch> out = new ArrayList<>();
        int[] alters = level.getAccidentals() ? new int[]{-1, 0, 1} : new int[]{0};
        for (int octave = 3; octave <= 6; octave++) {
            for (Letter letter : Letter.values()) {
                for (int a : alters) {
                    // Skip awkward spellings in a beginner drill (E♯, B♯, F♭, C♭).
                    if (a == 1 && (letter == Letter.E || letter == Letter.B)) continue;
                    if (a == -1 && (letter == Letter.F || letter == Letter.C)) continue;
                    SpelledPitch p = new SpelledPitch(letter, a, octave);
                    if (p.getMidi() >= level.getLowWrittenMidi() && p.getMidi() <= level.getHighWrittenMidi()) out.add(p);
                }
            }
        }
        return out;
    }

    /** A round of written notes, deterministic for a seed, never the same note twice in a row. */
    public static List<SpelledPitch> round(int levelNumber, int count, long seed) {
        List<SpelledPitch> pool = candidates(level(levelNumber));
        Random rnd = new Random(seed);
        List<SpelledPitch> out = new ArrayList<>();
        while (out.size() < count) {
            SpelledPitch p = pool.get(rnd.nextInt(pool.size()));
            if (out.isEmpty() || !out.get(out.size() - 1).equals(p)) out.add(p);
        }
        return out;
    }

    public static List<SpelledPitch> round(int levelNumber, long seed) { return round(levelNumber, DrillRules.READING_ROUND, seed); }

    /** The answer must name the written note with the right spelling (A♯ is not B♭ here). */
    public static boolean isCorrect(SpelledPitch question, PitchClass answer) { return question.getPitchClass().equals(answer); }
}
