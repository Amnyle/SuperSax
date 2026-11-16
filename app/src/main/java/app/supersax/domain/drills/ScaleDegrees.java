package app.supersax.domain.drills;

import app.supersax.domain.pitch.PitchClass;
import app.supersax.domain.pitch.SpelledPitch;
import app.supersax.domain.scale.ScaleGenerator;
import app.supersax.domain.scale.ScaleId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Scale-degree drill, in written pitch: "In D major, which note is the 5th degree?" (answer A), and from level 3 the
 * other way round too: "In F major, which degree is B♭?" (answer 4). The keys widen with the levels exactly as in the
 * scale-recognition drill: majors up to 1, 2, then 4 accidentals, then majors and minors up to 2, 4, then all keys.
 */
public final class ScaleDegrees {
    private ScaleDegrees() {}

    public static final int LEVELS = 6;
    public static final int ROUND = 10;

    public enum Kind { NOTE_OF_DEGREE, DEGREE_OF_NOTE }

    public static final class Question {
        private final Kind kind;
        private final ScaleId scale;
        private final int degree;

        public Question(Kind kind, ScaleId scale, int degree) { this.kind = kind; this.scale = scale; this.degree = degree; }

        public Kind getKind() { return kind; }
        public ScaleId getScale() { return scale; }
        /** 1 (tonic) to 7. */
        public int getDegree() { return degree; }
        /** The note on that degree, spelled as in the scale. */
        public PitchClass getNote() { return ScaleGenerator.oneOctave(scale, 4).get(degree - 1).getPitchClass(); }
        /** The scale's notes from the tonic, for the explanation after an answer. */
        public List<SpelledPitch> getNotes() { return ScaleGenerator.oneOctave(scale, 4); }
    }

    /** The keys of a level (same ladder as the scale-recognition drill's first six levels). */
    public static List<ScaleId> pool(int level) { return ScaleRecognition.pool(Math.max(1, Math.min(LEVELS, level))); }

    /** Share of "which degree is this note?" questions: none at levels 1–2, a third from level 3, half at level 6. */
    static double reverseShare(int level) { return level <= 2 ? 0 : level >= 6 ? 0.5 : 1.0 / 3; }

    public static List<Question> round(int level, int count, long seed) {
        Random rnd = new Random(seed);
        List<ScaleId> pool = pool(level);
        List<Question> out = new ArrayList<>();
        Question prev = null;
        while (out.size() < count) {
            ScaleId s = pool.get(rnd.nextInt(pool.size()));
            // The tonic is too easy to ask for: degrees 2 to 7.
            int degree = 2 + rnd.nextInt(6);
            Kind kind = rnd.nextDouble() < reverseShare(level) ? Kind.DEGREE_OF_NOTE : Kind.NOTE_OF_DEGREE;
            if (prev != null && prev.getScale().equals(s) && prev.getDegree() == degree) continue;
            prev = new Question(kind, s, degree);
            out.add(prev);
        }
        return out;
    }

    /** A note answer must be spelled as in the scale (F♯ in D major, not G♭). */
    public static boolean checkNote(Question q, PitchClass answer) { return q.getNote().equals(answer); }

    public static boolean checkDegree(Question q, int degree) { return q.getDegree() == degree; }
}
