package app.supersax.domain.solo;

import app.supersax.domain.library.Fraction;
import app.supersax.domain.library.Measure;
import app.supersax.domain.library.Score;
import app.supersax.domain.library.ScoreNote;
import app.supersax.domain.pitch.KeySignature;
import app.supersax.domain.pitch.PitchClass;
import app.supersax.domain.pitch.SpelledPitch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Writes a solo idea over a backing style, the way teachers build one: short phrases with space between them, a
 * rhythm motif that comes back, chord tones on the strong beats, steps and the odd leap in between, and an ending on
 * the key's root. Difficulty sets the rhythms, the notes allowed and the range:
 * EASY quarters and halves on the pentatonic, chord tones on the beat, one octave, lots of space;
 * MEDIUM adds eighths and the blue note, a motif repeated on new notes;
 * HARD swing-eighth lines on each chord's scale, 3rds and 7ths targeted, chromatic approach notes;
 * EXTREME sixteenths, enclosures around the targets, 9ths and 13ths, the full range.
 */
public final class SoloGenerator {
    private SoloGenerator() {}

    public enum Level { EASY, MEDIUM, HARD, EXTREME }

    /** One note (or rest when midi is null) of the solo: start and length in beats from the start of the solo, concert pitch. */
    public static final class Note {
        public final double start, beats;
        public final @Nullable Integer midi;
        Note(double start, double beats, @Nullable Integer midi) { this.start = start; this.beats = beats; this.midi = midi; }
        public double getStart() { return start; }
        public double getBeats() { return beats; }
        public @Nullable Integer getMidi() { return midi; }
    }

    /** Bars the solo lasts: one chorus, at least 8 bars. The rest of the loop is left for the player. */
    public static int bars(Groove.Style style) { return style.bars() >= 8 ? style.bars() : 8; }

    /** Written range (alto) per level. */
    static int[] writtenRange(Level l) {
        switch (l) {
            case EASY: return new int[]{62, 74};
            case MEDIUM: return new int[]{60, 79};
            case HARD: return new int[]{58, 84};
            default: return new int[]{58, 89};
        }
    }

    /** Rhythm cells of one bar (beats; negative = rest). Higher levels build bars beat by beat. */
    private static final double[][] EASY_BARS = {{2, 2}, {1, 1, 2}, {4}, {1, 1, 1, 1}, {2, 1, 1}, {-1, 1, 2}, {3, 1}, {1, 2, 1}};
    private static final double[][] MEDIUM_BARS = {{0.5, 0.5, 1, 2}, {1, 0.5, 0.5, 2}, {0.5, 0.5, 0.5, 0.5, 2}, {-0.5, 0.5, 1, 1, 1},
        {1.5, 0.5, 2}, {1, 1, 0.5, 0.5, 1}, {2, 0.5, 0.5, 1}, {-1, 0.5, 0.5, 2}};
    private static final double[][] HARD_BEATS = {{0.5, 0.5}, {0.5, 0.5}, {0.5, 0.5}, {1}, {-0.5, 0.5}};
    private static final double[][] EXTREME_BEATS = {{0.25, 0.25, 0.25, 0.25}, {0.5, 0.5}, {0.5, 0.25, 0.25}, {0.25, 0.25, 0.5}, {0.5, 0.5}, {1}};

    private static double[] barRhythm(Level l, Random rnd, boolean phraseEnd) {
        if (l == Level.EASY) return EASY_BARS[rnd.nextInt(EASY_BARS.length)];
        if (l == Level.MEDIUM) return MEDIUM_BARS[rnd.nextInt(MEDIUM_BARS.length)];
        double[][] cells = l == Level.HARD ? HARD_BEATS : EXTREME_BEATS;
        List<Double> out = new ArrayList<>();
        int beats = phraseEnd ? 2 : 4;
        for (int b = 0; b < beats; b++) for (double d : cells[rnd.nextInt(cells.length)]) out.add(d);
        if (phraseEnd) out.add(2.0);
        double[] a = new double[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    /** Pitch classes (semitones above the concert key) allowed over [ch]. */
    static int[] pool(Groove.Style style, Groove.Chord ch, Level l) {
        boolean bluesy = style != Groove.Style.JAZZ_251;
        if (l == Level.EASY) return bluesy ? new int[]{0, 3, 5, 7, 10} : new int[]{0, 2, 4, 7, 9};
        if (l == Level.MEDIUM) return bluesy ? new int[]{0, 3, 5, 6, 7, 10} : new int[]{0, 2, 4, 7, 9, 11};
        int r = ch.semitones;
        int[] mode;
        switch (ch.quality) {
            case MIN7: mode = new int[]{0, 2, 3, 5, 7, 9, 10}; break;
            case MAJ7: mode = new int[]{0, 2, 4, 5, 7, 9, 11}; break;
            default: mode = bluesy ? new int[]{0, 2, 3, 4, 5, 7, 9, 10} : new int[]{0, 2, 4, 5, 7, 9, 10};
        }
        int[] out = new int[mode.length];
        for (int i = 0; i < mode.length; i++) out[i] = (mode[i] + r) % 12;
        return out;
    }

    /** Chord tones to land on (semitones above the key), most wanted first. */
    static int[] targets(Groove.Chord ch, Level l) {
        int r = ch.semitones;
        int third = ch.quality.tones[1][1], seventh = ch.quality.tones[3][1];
        int[] rel;
        switch (l) {
            case EASY: rel = new int[]{0, 7, third}; break;
            case MEDIUM: rel = new int[]{third, 0, 7, seventh}; break;
            case HARD: rel = new int[]{third, seventh, 7}; break;
            default: rel = ch.quality == Groove.Quality.DOM7 ? new int[]{third, seventh, 14, 21} : new int[]{third, seventh, 14};
        }
        int[] out = new int[rel.length];
        for (int i = 0; i < rel.length; i++) out[i] = (rel[i] + r) % 12;
        return out;
    }

    private static boolean in(int pc, int[] set) { for (int s : set) if (s == pc) return true; return false; }

    /** Nearest pitch to [from] whose class (relative to [key]) is in [set], within [lo, hi]; random tie-break. */
    private static int nearest(int from, int key, int[] set, int lo, int hi, Random rnd) {
        int best = -1, bestD = 99;
        for (int m = lo; m <= hi; m++) {
            if (!in(Math.floorMod(m - key, 12), set)) continue;
            int d = Math.abs(m - from) + (rnd.nextInt(3) == 0 ? 1 : 0);
            if (d < bestD) { bestD = d; best = m; }
        }
        return best < 0 ? Math.max(lo, Math.min(hi, from)) : best;
    }

    /** The next pitch of [set] above (dir > 0) or below [from], [steps] scale steps away. */
    private static int step(int from, int key, int[] set, int dir, int steps) {
        int m = from;
        for (int s = 0; s < steps; s++) {
            do { m += dir; } while (!in(Math.floorMod(m - key, 12), set) && Math.abs(m - from) < 13);
        }
        return m;
    }

    /**
     * A solo over [style] in the concert key [key] (pitch class), for an alto saxophone, at [level]. The same seed
     * gives the same solo.
     */
    public static List<Note> generate(Groove.Style style, PitchClass key, Level level, long seed) {
        Random rnd = new Random(seed * 31 + level.ordinal());
        int k = key.getSemitone();
        int[] range = writtenRange(level);
        int lo = range[0] - 9, hi = range[1] - 9;
        int bars = bars(style);
        // The last bar on the I chord: the solo resolves there.
        int endBar = bars - 1;
        for (int b = bars - 1; b >= 0; b--) if (style.form.get(b % style.bars()).semitones == 0) { endBar = b; break; }
        List<Note> out = new ArrayList<>();
        int pitch = nearest((lo + hi) / 2, k, new int[]{0}, lo, hi, rnd);
        int dir = rnd.nextBoolean() ? 1 : -1;
        double[][] motif = new double[2][];
        boolean reuse = level == Level.EASY || level == Level.MEDIUM || rnd.nextBoolean();
        int phraseLen = level == Level.EXTREME ? 4 : 2;
        for (int bar = 0; bar < bars; bar++) {
            Groove.Chord ch = style.form.get(bar % style.bars());
            double t0 = bar * 4;
            int inPhrase = bar % (phraseLen * 2);
            // Phrases of [phraseLen] bars; easy and medium leave the next bars free (space to answer).
            boolean rest = (level == Level.EASY || level == Level.MEDIUM) && inPhrase >= phraseLen && bar != endBar;
            if (bar > endBar) rest = true;
            if (rest) { out.add(new Note(t0, 4, null)); continue; }
            if (bar == endBar) {
                int root = nearest(pitch, k, new int[]{0}, lo, hi, rnd);
                if (level != Level.EASY) {
                    int approach = root + (rnd.nextBoolean() ? 1 : -1);
                    if (level == Level.HARD || level == Level.EXTREME) {
                        out.add(new Note(t0 - 0.5, 0.5, approach)); // replaces nothing: the bar before ends on a rest or long note
                    }
                }
                out.add(new Note(t0, 3, root));
                out.add(new Note(t0 + 3, 1, null));
                pitch = root;
                continue;
            }
            boolean phraseEnd = (inPhrase % phraseLen) == phraseLen - 1 || bar + 1 == endBar;
            double[] rhythm;
            int slot = inPhrase % phraseLen;
            if (reuse && slot < 2 && motif[slot] != null && bar >= phraseLen * 2) rhythm = motif[slot];
            else {
                rhythm = barRhythm(level, rnd, phraseEnd && level.ordinal() >= 2);
                if (bar < 2 && slot < 2) motif[slot] = rhythm;
            }
            int[] pool = pool(style, ch, level);
            int[] targets = targets(ch, level);
            double t = t0;
            List<Note> barNotes = new ArrayList<>();
            for (int i = 0; i < rhythm.length; i++) {
                double d = Math.abs(rhythm[i]);
                if (rhythm[i] < 0) { barNotes.add(new Note(t, d, null)); t += d; continue; }
                boolean strong = Math.abs(t - Math.rint(t)) < 1e-9 && ((int) Math.rint(t - t0)) % 2 == 0;
                int next;
                if (strong || (i == rhythm.length - 1 && d >= 1)) {
                    // Land on a chord tone near where the line is going.
                    int[] want = Arrays.copyOf(targets, rnd.nextInt(targets.length) + 1);
                    next = nearest(pitch + dir * 2, k, want, lo, hi, rnd);
                } else {
                    double r = rnd.nextDouble();
                    if (r < 0.25) dir = -dir;
                    int steps = r < 0.85 ? 1 : 2;
                    next = step(pitch, k, pool, dir, steps);
                    if (rnd.nextDouble() < (level == Level.EASY ? 0.05 : 0.12)) next = nearest(pitch + dir * 5, k, targets, lo, hi, rnd); // a leap
                }
                if (next < lo || next > hi) { dir = -dir; next = step(pitch, k, pool, dir, 1); }
                next = Math.max(lo, Math.min(hi, next));
                // Chromatic approach into the next strong beat (hard and extreme).
                if (level.ordinal() >= 2 && i + 1 < rhythm.length && rhythm[i + 1] > 0 && d <= 0.5 && rnd.nextDouble() < 0.3) {
                    double tn = t + d;
                    if (Math.abs(tn - Math.rint(tn)) < 1e-9 && ((int) Math.rint(tn - t0)) % 2 == 0) {
                        int tgt = nearest(next, k, targets, lo, hi, rnd);
                        next = tgt + (rnd.nextBoolean() ? 1 : -1);
                        if (level == Level.EXTREME && i > 0 && !barNotes.isEmpty() && barNotes.get(barNotes.size() - 1).midi != null
                            && barNotes.get(barNotes.size() - 1).beats <= 0.5) {
                            // Enclosure: the note before goes to the other side of the target.
                            Note prev = barNotes.remove(barNotes.size() - 1);
                            barNotes.add(new Note(prev.start, prev.beats, tgt + (next > tgt ? -1 : 2)));
                        }
                    }
                }
                barNotes.add(new Note(t, d, next));
                dir = next > pitch ? 1 : next < pitch ? -1 : dir;
                pitch = next;
                t += d;
            }
            out.addAll(barNotes);
        }
        // An approach note added before the last bar overlaps the bar before: shorten what was there.
        out.sort((a, b) -> Double.compare(a.start, b.start));
        List<Note> clean = new ArrayList<>();
        for (Note n : out) {
            if (!clean.isEmpty()) {
                Note p = clean.get(clean.size() - 1);
                if (p.start + p.beats > n.start + 1e-9) {
                    clean.remove(clean.size() - 1);
                    if (n.start - p.start > 1e-9) clean.add(new Note(p.start, n.start - p.start, p.midi));
                }
            }
            // Approach and enclosure notes can step just outside the range: bring them back an octave.
            if (n.midi != null && (n.midi < lo || n.midi > hi)) n = new Note(n.start, n.beats, n.midi < lo ? n.midi + 12 : n.midi - 12);
            clean.add(n);
        }
        return clean;
    }

    // --- display -----------------------------------------------------------------------------

    /** Letter steps above the tonic for each semitone above it: blues spelling (♭3, ♭5, ♭7 rather than ♯2, ♯4, ♯6). */
    private static final int[] STEPS = {0, 1, 1, 2, 2, 3, 4, 4, 5, 5, 6, 6};

    /** [midi] spelled in the key whose tonic is [tonic] (written pitch). */
    static SpelledPitch spell(int midi, PitchClass tonic) {
        int rel = Math.floorMod(midi - tonic.getSemitone(), 12);
        PitchClass pc = Blues.above(tonic, STEPS[rel], rel);
        int base = pc.getLetter().getSemitone() + pc.getAlter();
        int oct = Math.floorDiv(midi - base, 12) - 1;
        return new SpelledPitch(pc.getLetter(), pc.getAlter(), oct);
    }

    /** Written key signature (alto) for the style in [concertKey]: the major key, or the relative major for minor styles. */
    public static int writtenFifths(Groove.Style style, PitchClass concertKey) {
        PitchClass w = Blues.altoWritten(concertKey);
        if (style.minor) w = Blues.above(w, 2, 3);
        return KeySignature.majorFifths(w);
    }

    /** The solo written for alto (a major sixth above concert), bar by bar, in 4/4. */
    public static Score toScore(List<Note> notes, Groove.Style style, PitchClass concertKey, int bpm) {
        int fifths = writtenFifths(style, concertKey);
        PitchClass tonic = Blues.altoWritten(concertKey);
        int bars = bars(style);
        List<Measure> measures = new ArrayList<>();
        for (int b = 0; b < bars; b++) {
            List<ScoreNote> m = new ArrayList<>();
            double t = b * 4;
            for (Note n : notes) {
                if (n.start + 1e-9 < b * 4 || n.start >= b * 4 + 4 - 1e-9) continue;
                if (n.start > t + 1e-9) m.add(new ScoreNote(null, sixteenths(n.start - t), false));
                double len = Math.min(n.beats, b * 4 + 4 - n.start);
                m.add(new ScoreNote(n.midi == null ? null : spell(n.midi + 9, tonic), sixteenths(len), false));
                t = n.start + len;
            }
            if (t < b * 4 + 4 - 1e-9) m.add(new ScoreNote(null, sixteenths(b * 4 + 4 - t), false));
            measures.add(new Measure(merge(m)));
        }
        return new Score(fifths, 4, 4, bpm, measures);
    }

    private static Fraction sixteenths(double beats) { return new Fraction((int) Math.round(beats * 4), 16); }

    /** Joins neighbouring rests so a silent bar shows one whole rest. */
    private static List<ScoreNote> merge(List<ScoreNote> in) {
        List<ScoreNote> out = new ArrayList<>();
        for (ScoreNote n : in) {
            if (!out.isEmpty() && n.getPitch() == null && out.get(out.size() - 1).getPitch() == null) {
                ScoreNote p = out.remove(out.size() - 1);
                Fraction sum = p.getDuration().plus(n.getDuration());
                // Keep rests a writable length (1/16, 1/8, 1/4, 3/8, 1/2, 3/4, 1).
                if (Arrays.asList("1/16", "1/8", "3/16", "1/4", "3/8", "1/2", "3/4", "1/1", "1").contains(sum.toString())) { out.add(new ScoreNote(null, sum, false)); continue; }
                out.add(p);
            }
            out.add(n);
        }
        return out;
    }
}
