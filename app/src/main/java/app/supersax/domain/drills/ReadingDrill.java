package app.supersax.domain.drills;

import app.supersax.domain.pitch.KeySignature;
import app.supersax.domain.pitch.Letter;
import app.supersax.domain.pitch.PitchClass;
import app.supersax.domain.pitch.SpelledPitch;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Note-reading drill: a written note on the treble staff, answered with a note-name keypad and an
 * accidental toggle. Levels widen the range, then read in keys: 1 staff only (E4–F5), 2 + one ledger
 * line each side (C4–A5), 3 the whole alto range (B♭3–F♯6), 4 in a key signature of 1 to 3 sharps or flats
 * (C4–A5): the note carries no accidental and the key decides it, 5 key signatures up to 5 over the whole
 * range, with now and then an accidental in the bar (a natural cancelling the key, or a sharp or flat).
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

    /** A note to read in a key signature ([fifths]: sharps > 0, flats < 0). */
    public static final class Question {
        private final SpelledPitch pitch;
        private final int fifths;
        public Question(SpelledPitch pitch, int fifths) { this.pitch = pitch; this.fifths = fifths; }
        public SpelledPitch getPitch() { return pitch; }
        public int getFifths() { return fifths; }
        @Override public boolean equals(Object o) { return o instanceof Question && ((Question) o).pitch.equals(pitch) && ((Question) o).fifths == fifths; }
        @Override public int hashCode() { return pitch.hashCode() * 31 + fifths; }
    }

    /**
     * Questions of a level. Levels 1–3: naturals without key signature. Level 4: a key of 1–3 sharps or flats
     * (changing every few notes), notes written without accidental, most of them on a letter the key alters.
     * Level 5: keys up to 5, the whole range, and one note in four with its own accidental.
     */
    public static List<Question> questions(int levelNumber, int count, long seed) {
        List<Question> out = new ArrayList<>();
        if (levelNumber <= 3) {
            for (SpelledPitch p : round(levelNumber, count, seed)) out.add(new Question(p, 0));
            return out;
        }
        Random rnd = new Random(seed);
        boolean five = levelNumber >= 5;
        int lo = five ? 58 : 60, hi = five ? 90 : 81;
        int maxKey = five ? 5 : 3;
        int fifths = 0;
        while (out.size() < count) {
            if (out.size() % 4 == 0) { do { fifths = rnd.nextInt(2 * maxKey + 1) - maxKey; } while (fifths == 0); }
            KeySignature key = new KeySignature(fifths);
            boolean wantAltered = rnd.nextDouble() < 0.65;
            List<SpelledPitch> pool = new ArrayList<>();
            for (int octave = 3; octave <= 6; octave++) {
                for (Letter letter : Letter.values()) {
                    int keyAlter = key.alterFor(letter);
                    if (wantAltered != (keyAlter != 0)) continue;
                    int alter = keyAlter;
                    if (five && rnd.nextDouble() < 0.25) {
                        // An accidental in the bar: cancel the key, or alter a letter the key leaves natural.
                        alter = keyAlter != 0 ? 0 : (fifths > 0 ? 1 : -1);
                        if (alter == 1 && (letter == Letter.E || letter == Letter.B)) alter = keyAlter;
                        if (alter == -1 && (letter == Letter.F || letter == Letter.C)) alter = keyAlter;
                    }
                    SpelledPitch p = new SpelledPitch(letter, alter, octave);
                    if (p.getMidi() >= lo && p.getMidi() <= hi) pool.add(p);
                }
            }
            if (pool.isEmpty()) continue;
            SpelledPitch p = pool.get(rnd.nextInt(pool.size()));
            Question q = new Question(p, fifths);
            if (out.isEmpty() || !out.get(out.size() - 1).equals(q)) out.add(q);
        }
        return out;
    }
}
