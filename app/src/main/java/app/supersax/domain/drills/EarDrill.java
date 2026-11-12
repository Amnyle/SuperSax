package app.supersax.domain.drills;

import app.supersax.domain.pitch.PitchClass;
import app.supersax.domain.pitch.SpelledPitch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Ear training: hear and recognise. Levels: 1 higher or lower; 2 intervals (major 2nd, major 3rd,
 * 5th, octave); 3 adds minor 3rd, 4th, major 6th; 4 every interval up to the octave; 5 major or minor
 * chord; 6 major, minor, dominant 7, minor 7, major 7; 7 three-note melodies on the major scale;
 * 8 four-note melodies on the minor pentatonic. Notes are written pitches (alto); the app plays them
 * at concert pitch.
 */
public final class EarDrill {
    private EarDrill() {}

    public static final int LEVELS = 8;
    public static final int ROUND = 10;

    public enum Kind { DIRECTION, INTERVAL, CHORD, MELODY }

    /** Chord types of levels 5 and 6 (semitones above the root). */
    public enum ChordType {
        MAJOR(0, 4, 7), MINOR(0, 3, 7), DOM7(0, 4, 7, 10), MIN7(0, 3, 7, 10), MAJ7(0, 4, 7, 11);
        public final int[] semis;
        ChordType(int... semis) { this.semis = semis; }
    }

    /** One question: the written notes to play, and the right answer (an option index or the melody itself). */
    public static final class Question {
        private final Kind kind;
        private final List<Integer> writtenMidi;
        private final List<Integer> options;
        private final int answer;
        Question(Kind kind, List<Integer> writtenMidi, List<Integer> options, int answer) {
            this.kind = kind; this.writtenMidi = writtenMidi; this.options = options; this.answer = answer;
        }
        public Kind getKind() { return kind; }
        /** Notes in order (melodic) or together (chord). */
        public List<Integer> getWrittenMidi() { return writtenMidi; }
        /** DIRECTION: 1 higher, −1 lower; INTERVAL: semitones; CHORD: ChordType ordinals. Empty for melodies. */
        public List<Integer> getOptions() { return options; }
        /** Index of the right option. */
        public int getAnswer() { return answer; }
        /** The note to play back on the saxophone (the second note, or the last note of a melody). */
        public int getTargetWrittenMidi() { return writtenMidi.get(writtenMidi.size() - 1); }
    }

    public static List<Integer> intervals(int level) {
        switch (level) {
            case 2: return Arrays.asList(2, 4, 7, 12);
            case 3: return Arrays.asList(3, 4, 5, 7, 9, 12);
            default: return Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        }
    }

    public static Kind kind(int level) {
        if (level <= 1) return Kind.DIRECTION;
        if (level <= 4) return Kind.INTERVAL;
        if (level <= 6) return Kind.CHORD;
        return Kind.MELODY;
    }

    public static List<Question> round(int level, int count, long seed) {
        Random rnd = new Random(seed);
        List<Question> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(question(level, rnd));
        return out;
    }

    public static List<Question> round(int level, long seed) { return round(level, ROUND, seed); }

    private static Question question(int level, Random rnd) {
        int low = 62 + rnd.nextInt(8); // written D4..A4
        switch (kind(level)) {
            case DIRECTION: {
                int step = 2 + rnd.nextInt(6);
                boolean up = rnd.nextBoolean();
                List<Integer> opts = Arrays.asList(1, -1);
                return new Question(Kind.DIRECTION, Arrays.asList(low + 2, low + 2 + (up ? step : -step)), opts, up ? 0 : 1);
            }
            case INTERVAL: {
                List<Integer> opts = intervals(level);
                int a = rnd.nextInt(opts.size());
                return new Question(Kind.INTERVAL, Arrays.asList(low, low + opts.get(a)), opts, a);
            }
            case CHORD: {
                List<Integer> opts = new ArrayList<>();
                int types = level == 5 ? 2 : ChordType.values().length;
                for (int t = 0; t < types; t++) opts.add(t);
                int a = rnd.nextInt(types);
                List<Integer> notes = new ArrayList<>();
                for (int s : ChordType.values()[a].semis) notes.add(low - 2 + s);
                return new Question(Kind.CHORD, notes, opts, a);
            }
            default: {
                int[] scale = level == 7 ? new int[]{0, 2, 4, 5, 7, 9, 11, 12} : new int[]{0, 3, 5, 7, 10, 12};
                int len = level == 7 ? 3 : 4;
                int tonic = 60 + rnd.nextInt(8);
                List<Integer> notes = new ArrayList<>();
                int idx = rnd.nextInt(3);
                for (int k = 0; k < len; k++) {
                    notes.add(tonic + scale[idx]);
                    idx = Math.max(0, Math.min(scale.length - 1, idx + (rnd.nextBoolean() ? 1 : -1) * (1 + rnd.nextInt(2))));
                }
                return new Question(Kind.MELODY, notes, new ArrayList<>(), -1);
            }
        }
    }

    /** A melody answer: the tapped pitch classes in order, enharmonics accepted (it is heard, not read). */
    public static boolean checkMelody(Question q, List<PitchClass> tapped) {
        if (tapped.size() != q.getWrittenMidi().size()) return false;
        for (int i = 0; i < tapped.size(); i++) {
            if (Math.floorMod(tapped.get(i).getSemitone(), 12) != Math.floorMod(q.getWrittenMidi().get(i), 12)) return false;
        }
        return true;
    }

    /** Spelled written pitches of the question, for showing the answer. */
    public static List<SpelledPitch> spelled(Question q) {
        List<SpelledPitch> out = new ArrayList<>();
        for (int m : q.getWrittenMidi()) out.add(SpelledPitch.fromMidi(m));
        return out;
    }
}
