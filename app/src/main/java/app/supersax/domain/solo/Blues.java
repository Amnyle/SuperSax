package app.supersax.domain.solo;

import app.supersax.domain.pitch.Letter;
import app.supersax.domain.pitch.PitchClass;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The 12-bar blues for soloing practice: the form (I7 I7 I7 I7 | IV7 IV7 I7 I7 | V7 IV7 I7 V7), the
 * blues scale (1 ♭3 4 ♭5 5 ♭7) and each chord's tones, spelled properly. Keys are concert pitch; the
 * alto saxophone reads them a major sixth higher (concert B♭ blues = written G).
 */
public final class Blues {
    private Blues() {}

    /** Chord degree of each bar: 0 = I, 3 = IV, 4 = V (letter steps above the root). */
    public static final List<Integer> FORM = Collections.unmodifiableList(Arrays.asList(0, 0, 0, 0, 3, 3, 0, 0, 4, 3, 0, 4));
    private static final int[][] SCALE = {{0, 0}, {2, 3}, {3, 5}, {4, 6}, {4, 7}, {6, 10}};
    private static final int[][] DOM7 = {{0, 0}, {2, 4}, {4, 7}, {6, 10}};

    /** Concert keys offered (common blues keys for saxophone). */
    public static final List<String> KEYS = Collections.unmodifiableList(Arrays.asList("Bb", "F", "Eb", "C", "G"));

    /** The note [letterSteps] letters and [semitones] above [root], spelled from the letter. */
    public static PitchClass above(PitchClass root, int letterSteps, int semitones) {
        Letter l = Letter.ofIndex(root.getLetter().ordinal() + letterSteps);
        int natural = Math.floorMod(l.getSemitone() - root.getLetter().getSemitone(), 12);
        int alter = root.getAlter() + semitones - natural;
        if (alter > 6) alter -= 12;
        if (alter < -6) alter += 12;
        return new PitchClass(l, alter);
    }

    /** Written key for alto from a concert key: a major sixth up. */
    public static PitchClass altoWritten(PitchClass concert) { return above(concert, 5, 9); }

    /** Root of the chord of bar [bar] (0..11). */
    public static PitchClass chordRoot(PitchClass key, int bar) {
        int degree = FORM.get(bar);
        int semis = degree == 0 ? 0 : degree == 3 ? 5 : 7;
        return above(key, degree, semis);
    }

    /** Root, 3rd, 5th and ♭7th of a dominant seventh chord. */
    public static List<PitchClass> chordTones(PitchClass root) {
        List<PitchClass> out = new ArrayList<>();
        for (int[] s : DOM7) out.add(above(root, s[0], s[1]));
        return out;
    }

    /** The target note on a chord change: its 3rd (the note that names the chord). */
    public static PitchClass target(PitchClass key, int bar) { return chordTones(chordRoot(key, bar)).get(1); }

    /** The six notes of the blues scale. */
    public static List<PitchClass> scale(PitchClass key) {
        List<PitchClass> out = new ArrayList<>();
        for (int[] s : SCALE) out.add(above(key, s[0], s[1]));
        return out;
    }

    /** The first three notes for a beginner's solo: root, ♭3 and 4. */
    public static List<PitchClass> threeNotes(PitchClass key) { return scale(key).subList(0, 3); }

    /** One note of a call phrase: start and length in beats from the start of the phrase; semitones above the key's root. */
    public static final class PhraseNote {
        private final double startBeat, beats;
        private final int semitones;
        public PhraseNote(double startBeat, double beats, int semitones) { this.startBeat = startBeat; this.beats = beats; this.semitones = semitones; }
        public double getStartBeat() { return startBeat; }
        public double getBeats() { return beats; }
        public int getSemitones() { return semitones; }
    }

    /**
     * A generated 2-bar call phrase on the blues scale (3 to 6 notes, eighths and quarters, rests
     * allowed), ending on the root, ♭3 or 5 and leaving the last beats empty so it breathes.
     */
    public static List<PhraseNote> callPhrase(long seed) {
        Random rnd = new Random(seed);
        int[] steps = {0, 3, 5, 6, 7, 10, 12};
        List<PhraseNote> out = new ArrayList<>();
        int notes = 3 + rnd.nextInt(4);
        double t = rnd.nextBoolean() ? 0 : 0.5;
        int idx = rnd.nextInt(steps.length);
        for (int i = 0; i < notes && t < 6; i++) {
            double len = new double[]{0.5, 0.5, 1, 1, 1.5}[rnd.nextInt(5)];
            boolean last = i == notes - 1 || t + len >= 6;
            int st = last ? new int[]{0, 3, 7, 12}[rnd.nextInt(4)] : steps[idx];
            out.add(new PhraseNote(t, Math.min(len, 6.5 - t), st));
            if (last) break;
            idx = Math.max(0, Math.min(steps.length - 1, idx + (rnd.nextInt(3) - 1) * (1 + rnd.nextInt(2))));
            t += len + (rnd.nextInt(4) == 0 ? 0.5 : 0);
        }
        return out;
    }
}
