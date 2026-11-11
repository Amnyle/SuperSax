package app.supersax.domain.drills;

import app.supersax.domain.pitch.Interval;
import app.supersax.domain.pitch.KeySignature;
import app.supersax.domain.pitch.PitchClass;
import app.supersax.domain.pitch.SpelledPitch;
import app.supersax.domain.scale.ScaleGenerator;
import app.supersax.domain.scale.ScaleId;
import app.supersax.domain.scale.ScaleType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Scale-recognition drill (visual), all in written pitch: name the major or minor key of a key signature,
 * name a scale from its notes (tonic and type), or build a scale from a root by tapping its notes. Levels
 * widen the keys (1: up to 1 accidental, 2: 2, 3: 4, 4: 6) and add minors (3+) and modes (5).
 */
public final class ScaleRecognition {
    private ScaleRecognition() {}

    public enum Kind { NAME_FROM_SIGNATURE, NAME_FROM_NOTES, BUILD }

    public static final class Question {
        private final Kind kind;
        private final ScaleId scale;
        private final KeySignature signature;

        public Question(Kind kind, ScaleId scale, KeySignature signature) { this.kind = kind; this.scale = scale; this.signature = signature; }

        public Kind getKind() { return kind; }
        public ScaleId getScale() { return scale; }
        public KeySignature getSignature() { return signature; }

        /** Written notes, one octave up from the tonic in octave 4. */
        public List<SpelledPitch> getNotes() { return ScaleGenerator.oneOctave(scale, 4); }
    }

    /** Number of levels: each one adds keys or scale types to the mix. */
    public static final int LEVELS = 8;

    /**
     * Most sharps or flats in the key signature at a level. Levels: 1–3 major (1, 2, then 4 accidentals),
     * 4–6 major and minor (2, 4, then all keys), 7 adds Dorian and Mixolydian, 8 all modes.
     */
    public static int maxAccidentals(int level) {
        switch (level) {
            case 1: return 1;
            case 2: return 2;
            case 3: return 4;
            case 4: return 2;
            case 5: return 4;
            case 7: return 4;
            default: return 6;
        }
    }

    public static List<ScaleType> types(int level) {
        if (level >= 8) return Arrays.asList(ScaleType.MAJOR, ScaleType.NATURAL_MINOR, ScaleType.DORIAN, ScaleType.MIXOLYDIAN, ScaleType.LYDIAN, ScaleType.PHRYGIAN);
        if (level == 7) return Arrays.asList(ScaleType.MAJOR, ScaleType.NATURAL_MINOR, ScaleType.DORIAN, ScaleType.MIXOLYDIAN);
        if (level >= 4) return Arrays.asList(ScaleType.MAJOR, ScaleType.NATURAL_MINOR);
        return Arrays.asList(ScaleType.MAJOR);
    }

    /** Written scales allowed at a level: tonics whose key signature has at most the level's accidentals. */
    public static List<ScaleId> pool(int level) {
        List<ScaleId> out = new ArrayList<>();
        for (ScaleType t : types(level)) {
            for (int f = -6; f <= 6; f++) {
                if (Math.abs(f) > maxAccidentals(level)) continue;
                PitchClass majorTonic = new KeySignature(f).getMajorTonic();
                SpelledPitch major = new SpelledPitch(majorTonic.getLetter(), majorTonic.getAlter(), 4);
                int degree;
                switch (t) {
                    case DORIAN: degree = 1; break;
                    case PHRYGIAN: degree = 2; break;
                    case LYDIAN: degree = 3; break;
                    case MIXOLYDIAN: degree = 4; break;
                    case NATURAL_MINOR: degree = 5; break;
                    default: degree = 0;
                }
                out.add(new ScaleId(major.transpose(new Interval(degree, ScaleType.MAJOR.getSemitones().get(degree))).getPitchClass(), t));
            }
        }
        return out;
    }

    public static List<Question> round(int level, int count, long seed) {
        Random rnd = new Random(seed);
        List<ScaleId> pool = pool(level);
        Kind[] kinds = Kind.values();
        List<Question> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ScaleId s = pool.get(rnd.nextInt(pool.size()));
            Kind kind = kinds[i % kinds.length];
            // Key-signature questions only ask major or minor (modes need the notes).
            if (kind == Kind.NAME_FROM_SIGNATURE && s.getType().getFamily() == ScaleType.Family.MODE) kind = Kind.NAME_FROM_NOTES;
            out.add(new Question(kind, s, s.getKeySignature()));
        }
        return out;
    }

    public static List<Question> round(int level, long seed) { return round(level, DrillRules.SCALE_ROUND, seed); }

    /** Naming answer: tonic spelled exactly, and the type. */
    public static boolean checkName(Question q, PitchClass tonic, ScaleType type) { return q.getScale().getTonic().equals(tonic) && q.getScale().getType() == type; }

    /** Building answer: the tapped notes (pitch classes), tonic to tonic, in order. */
    public static boolean checkBuild(Question q, List<PitchClass> tapped) {
        List<PitchClass> expected = new ArrayList<>();
        for (SpelledPitch p : q.getNotes()) expected.add(p.getPitchClass());
        return tapped.equals(expected);
    }
}
