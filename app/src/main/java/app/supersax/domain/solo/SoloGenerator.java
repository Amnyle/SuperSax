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
 * Writes a solo idea over a backing style the way players build one: from vocabulary (licks and riffs), placed in a
 * form (call, repeat of the call, answer, ending on a 12-bar blues; a riff and its variations in funk; a line through
 * each ii–V–I in jazz), each phrase starting near where the last one ended, with space between phrases.
 * EASY: quarter and half notes on the pentatonic, two-bar calls, two bars of space to answer.
 * MEDIUM: classic eighth-note blues licks with the blue note and the ♭3→3 slide.
 * HARD: swing-eighth lines on each chord's scale (arpeggios, approach notes into the 3rd), so the line follows the chords.
 * EXTREME: sixteenth-note runs, enclosures (above, below, target), double-time lines through the chords.
 */
public final class SoloGenerator {
    private SoloGenerator() {}

    public enum Level { EASY, MEDIUM, HARD, EXTREME }

    /** One note (or rest when midi is null): start and length in beats from the start of the solo, concert pitch, loudness. */
    public static final class Note {
        public final double start, beats, vel;
        public final @Nullable Integer midi;
        Note(double start, double beats, @Nullable Integer midi) { this(start, beats, midi, 1.0); }
        Note(double start, double beats, @Nullable Integer midi, double vel) { this.start = start; this.beats = beats; this.midi = midi; this.vel = vel; }
        public double getStart() { return start; }
        public double getBeats() { return beats; }
        public @Nullable Integer getMidi() { return midi; }
        public double getVel() { return vel; }
    }

    /** Bars the solo lasts: one chorus, at least 8 bars. The rest of the loop is left for the player. */
    public static int bars(Groove.Style style) { return style.bars() >= 8 ? style.bars() : 8; }

    /** Written range (alto) per level. */
    static int[] writtenRange(Level l) {
        switch (l) {
            case EASY: return new int[]{60, 77};
            case MEDIUM: return new int[]{58, 81};
            case HARD: return new int[]{58, 84};
            default: return new int[]{58, 89};
        }
    }

    // --- vocabulary -----------------------------------------------------------------------------
    // Key licks: "semitones above the key root:beats", r = rest. Chord licks: "scale step of the chord (0 = root,
    // 2 = 3rd, 7 = octave; a = a half step below, b = a half step above):beats".

    private static final String[] BLUES_EASY = {
        "0:1 3:1 5:2 3:1 0:1 r:2", "7:2 10:1 7:1 5:1 3:1 0:2", "12:2 10:2 7:3 r:1", "3:1 5:1 7:2 5:1 3:1 0:2",
        "r:1 0:1 3:1 0:1 -2:2 0:2", "7:1 7:1 10:2 7:2 r:2", "5:2 3:2 0:3 r:1", "r:2 7:1 5:1 3:2 0:2",
    };
    private static final String[] BLUES_EASY_END = {"3:2 -2:2 0:4", "7:1 5:1 3:1 0:1 0:4", "r:1 3:1 5:2 0:4"};
    private static final String[] BLUES_MEDIUM = {
        "r:.5 3:.5 5:.5 6:.5 7:1 5:1 3:.5 0:1.5 r:2", "12:.5 10:.5 7:.5 10:.5 7:1 5:1 3:2 0:2",
        "7:.5 6:.5 5:.5 3:.5 0:1 -2:1 0:2 r:2", "3:.5 4:.5 7:1 9:1 7:1 4:.5 3:.5 0:1 r:2",
        "0:1 3:.5 0:.5 3:1 5:1 6:.5 5:.5 3:.5 0:.5 0:2", "r:1 10:.5 12:.5 10:1 7:1 5:.5 7:.5 5:.5 3:.5 0:2",
        "7:.5 7:.5 r:.5 7:.5 10:1 7:1 5:1 3:1 r:2", "r:.5 0:.5 3:.5 5:.5 3:.5 0:.5 -2:1 0:2 r:2",
    };
    private static final String[] BLUES_MEDIUM_END = {"3:.5 4:.5 0:1 -2:1 -5:1 0:4", "6:.5 5:.5 3:.5 0:.5 -2:1 3:.5 4:.5 0:4",
        "10:.5 7:.5 5:.5 3:.5 5:.5 3:.5 0:1 0:4"};
    private static final String[] MAJOR_EASY = {"4:1 2:1 0:2 r:4", "7:2 9:1 7:1 4:2 r:2", "0:1 2:1 4:1 7:1 9:2 7:2", "r:1 4:1 7:1 4:1 2:2 r:2"};
    private static final String[] MAJOR_EASY_END = {"4:2 2:2 0:4", "7:1 4:1 2:2 0:4"};
    private static final String[] MAJOR_MEDIUM = {"r:.5 0:.5 2:.5 4:.5 7:.5 9:.5 7:.5 4:.5 2:1 0:1 r:2", "9:.5 7:.5 4:.5 2:.5 3:.5 4:.5 7:1 4:2 r:2",
        "4:1 5:.5 4:.5 2:.5 0:.5 -1:1 0:2 r:2", "r:1 7:.5 9:.5 12:1 9:.5 7:.5 4:1 2:1 r:2"};
    private static final String[] MAJOR_MEDIUM_END = {"4:.5 2:.5 0:1 -1:1 2:1 0:4", "9:.5 7:.5 4:.5 2:.5 3:.5 4:.5 2:1 0:4"};
    private static final String[] FUNK_EASY = {"0:1 r:1 3:1 5:1", "7:1 r:1 5:1 3:1", "0:1 0:1 r:1 3:1"};
    private static final String[] BLUES_SHORT = {"7:.5 5:.5 3:.5 0:.5 0:2", "r:.5 10:.5 7:.5 5:.5 7:2", "3:.5 4:.5 0:1 r:2"};
    private static final String[] MAJOR_SHORT = {"4:.5 2:.5 0:1 r:2", "7:.5 9:.5 7:1 r:2"};
    private static final String[] FUNK_MEDIUM = {"r:.5 0:.5 3:.5 0:.5 r:.5 5:.5 6:.5 7:.5", "0:.5 r:.5 10:.5 0:.5 r:.5 3:.5 r:.5 5:.5",
        "7:.5 7:.5 r:.5 10:.5 r:.5 7:.5 5:.5 3:.5"};
    private static final String[] FUNK_HARD = {"0:.25 r:.25 0:.25 3:.25 r:.5 5:.5 7:.25 10:.25 r:.5 12:.5 10:.5",
        "r:.25 7:.25 10:.25 7:.25 5:.5 3:.5 r:.5 0:.25 3:.25 5:.5 r:.5", "12:.25 10:.25 7:.25 5:.25 6:.25 5:.25 3:.5 0:.5 r:.5 -2:.25 0:.75"};
    private static final String[] FUNK_END = {"0:.25 3:.25 5:.25 6:.25 7:.5 5:.5 3:.5 0:1.5"};
    private static final String[] FUNK_EASY_END = {"3:1 5:1 0:2"};
    private static final String[] LINE_HARD = {
        "0:.5 1:.5 2:.5 4:.5 6:.5 4:.5 2:.5 0:.5", "2a:.5 2:.5 4:.5 6:.5 8:.5 6:.5 4:.5 2:.5", "7:.5 6:.5 5:.5 4:.5 3:.5 2:.5 1:.5 2:.5",
        "r:.5 4:.5 5:.5 6:.5 7:1 6:.5 4:.5", "6:.5 4:.5 2:.5 0:.5 -1:.5 0:.5 2:1", "4:.5 3:.5 2:.5 2a:.5 2:1 r:1",
        "9:.5 7:.5 6:.5 4:.5 2:1 r:1", "r:1 2:.5 4:.5 6:.5 5:.5 4:.5 2:.5",
    };
    private static final String[] LINE_HARD_END = {"4:.5 3:.5 2:.5 1:.5 2:2", "6:.5 4:.5 2a:.5 2:.5 0:2", "r:.5 2:.5 4:.5 6:.5 4:2"};
    private static final String[] LINE_EXTREME = {
        "0:.25 1:.25 2:.25 4:.25 6:.25 4:.25 2:.25 1:.25 2a:.5 2:.5 4:.5 6:.5", "7:.25 6:.25 5:.25 4:.25 3:.25 2:.25 1:.25 0:.25 2b:.25 2a:.25 2:.5 r:1",
        "4:.5 6:.25 8:.25 9:.5 8:.25 6:.25 4:.25 3:.25 2:.25 1:.25 2:1", "r:.5 2:.25 4:.25 6:.25 8:.25 10:.25 9:.25 8:.25 6:.25 4:.25 2:.25 1:.5 2:.5",
        "9:.25 8:.25 7:.25 6:.25 7b:.25 7a:.25 7:.5 6:.25 4:.25 2:.25 0:.25 -1:1",
    };
    private static final String[] LINE_EXTREME_END = {"4:.25 3:.25 2:.25 1:.25 0:.25 -1:.25 0:.5 2:2", "2b:.25 2a:.25 2:.5 4:.5 6:.5 4:2"};

    /** A parsed lick: per note {beats, value, chromatic offset} with value = Integer.MIN_VALUE for a rest. */
    private static final class Lick {
        final double[] beats; final int[] value; final int[] chroma; final boolean chord;
        Lick(String s, boolean chord) {
            String[] tok = s.trim().split("\\s+");
            beats = new double[tok.length]; value = new int[tok.length]; chroma = new int[tok.length];
            this.chord = chord;
            for (int i = 0; i < tok.length; i++) {
                String[] p = tok[i].split(":");
                beats[i] = Double.parseDouble(p[1]);
                String v = p[0];
                if (v.equals("r")) { value[i] = Integer.MIN_VALUE; continue; }
                if (v.endsWith("a")) { chroma[i] = -1; v = v.substring(0, v.length() - 1); }
                else if (v.endsWith("b")) { chroma[i] = 1; v = v.substring(0, v.length() - 1); }
                value[i] = Integer.parseInt(v);
            }
        }
        double length() { double s = 0; for (double b : beats) s += b; return s; }
    }

    private static final int[] MIXO = {0, 2, 4, 5, 7, 9, 10}, DORIAN = {0, 2, 3, 5, 7, 9, 10}, IONIAN = {0, 2, 4, 5, 7, 9, 11};

    /** Semitones above the key root of note [i] of [l] over chord [ch] (unplaced octave). */
    private static int semis(Lick l, int i, Groove.Chord ch, boolean minorStyle) {
        int v = l.value[i];
        if (!l.chord) {
            // Over IV, or in a minor key, the major 3rd of the key clashes: use the ♭3.
            if ((ch.semitones == 5 || minorStyle) && Math.floorMod(v, 12) == 4) v -= 1;
            if (minorStyle && Math.floorMod(v, 12) == 9) v -= 1;
            return v;
        }
        int[] scale = ch.quality == Groove.Quality.MIN7 ? DORIAN : ch.quality == Groove.Quality.MAJ7 ? IONIAN : MIXO;
        return ch.semitones + Math.floorDiv(v, 7) * 12 + scale[Math.floorMod(v, 7)] + l.chroma[i];
    }

    // --- form -----------------------------------------------------------------------------------

    private enum Part { CALL, REPEAT, ANSWER, LINE, LINE_END, RIFF, RIFF_AGAIN, RIFF_OTHER, END, REST }

    /** What happens in each bar (a part starting on a bar may last more than one bar; following bars are SKIP = null). */
    private static Part[] plan(Groove.Style style, Level l, int bars, Random rnd) {
        Part[] p = new Part[bars];
        java.util.Arrays.fill(p, Part.REST);
        if (style == Groove.Style.FUNK) {
            boolean busy = l.ordinal() >= 2;
            p[0] = Part.RIFF; p[1] = busy ? Part.RIFF_AGAIN : Part.REST; p[2] = Part.RIFF_OTHER; p[3] = Part.REST;
            p[4] = Part.RIFF_AGAIN; p[5] = busy ? Part.RIFF_OTHER : Part.REST; p[6] = Part.END; p[7] = Part.REST;
            return p;
        }
        boolean lines = l.ordinal() >= 2;
        if (bars == 12) {
            // A, A, B and the ending: the classic 12-bar shape.
            p[0] = Part.CALL; p[1] = null;
            p[4] = Part.REPEAT; p[5] = null;
            p[8] = Part.ANSWER; p[9] = null;
            p[10] = Part.END; p[11] = null;
            if (lines) { p[2] = Part.LINE_END; p[6] = Part.LINE_END; if (l == Level.EXTREME) { p[2] = Part.LINE; p[3] = Part.LINE_END; } }
            else if (l == Level.MEDIUM && rnd.nextBoolean()) p[3] = Part.LINE_END;
            return p;
        }
        // Jazz ii–V–I, twice: a line through ii and V landing on I, space, then a second line and the ending.
        if (lines) {
            p[0] = Part.LINE; p[1] = Part.LINE; p[2] = Part.LINE_END;
            p[4] = Part.LINE; p[5] = Part.LINE; p[6] = Part.END; p[7] = null;
            if (l == Level.EXTREME) p[3] = Part.LINE_END;
        } else {
            p[0] = Part.CALL; p[1] = null; p[4] = Part.REPEAT; p[5] = null; p[6] = Part.END; p[7] = null;
        }
        return p;
    }

    // --- generation -----------------------------------------------------------------------------

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
        boolean minor = style.minor;
        boolean major = style == Groove.Style.JAZZ_251;
        boolean funk = style == Groove.Style.FUNK;
        String[] calls, ends, riffs = null;
        boolean chordLines = level.ordinal() >= 2 && !funk;
        switch (level) {
            case EASY: calls = major ? MAJOR_EASY : BLUES_EASY; ends = funk ? FUNK_EASY_END : major ? MAJOR_EASY_END : BLUES_EASY_END; riffs = FUNK_EASY; break;
            case MEDIUM: calls = major ? MAJOR_MEDIUM : BLUES_MEDIUM; ends = funk ? FUNK_END : major ? MAJOR_MEDIUM_END : BLUES_MEDIUM_END; riffs = FUNK_MEDIUM; break;
            default: calls = major ? MAJOR_MEDIUM : BLUES_MEDIUM; ends = funk ? FUNK_END : major ? MAJOR_MEDIUM_END : BLUES_MEDIUM_END; riffs = FUNK_HARD;
        }
        String[] lines = level == Level.EXTREME ? LINE_EXTREME : LINE_HARD;
        String[] lineEnds = level == Level.EXTREME ? LINE_EXTREME_END : LINE_HARD_END;
        Part[] plan = plan(style, level, bars, rnd);

        List<Note> out = new ArrayList<>();
        int[] last = {k + 12 * Math.floorDiv(lo + hi - 2 * k, 24)};  // start around the middle of the range, on the root
        Lick call = null, riff = null;
        String callStr = null, riffStr = null;
        double t = 0;
        for (int bar = 0; bar < bars; bar++) {
            Part part = plan[bar];
            if (part == null) continue; // inside a two-bar part
            double t0 = bar * 4;
            if (t < t0 - 1e-9) { out.add(new Note(t, t0 - t, null)); t = t0; }
            if (t > t0 + 1e-9) continue;
            Lick lick;
            switch (part) {
                case CALL:
                    callStr = chordLines ? pick(lines, rnd) + " " + pick(lineEnds, rnd) : pick(calls, rnd);
                    call = new Lick(callStr, chordLines);
                    lick = call; break;
                case REPEAT:
                    if (call == null) { lick = new Lick(pick(calls, rnd), false); break; }
                    // Same start, new ending (from medium on, half the time): the call is answered by itself.
                    if (level != Level.EASY && rnd.nextBoolean()) {
                        String end = chordLines ? pick(lineEnds, rnd) : pick(major ? MAJOR_SHORT : BLUES_SHORT, rnd);
                        lick = new Lick(firstBar(callStr) + " " + end, chordLines);
                    } else lick = call;
                    break;
                case ANSWER:
                    lick = chordLines ? new Lick(pickOther(lines, callStr, rnd) + " " + pick(lineEnds, rnd), true) : new Lick(pickOther(calls, callStr, rnd), false);
                    break;
                case LINE: lick = new Lick(pick(lines, rnd), true); break;
                case LINE_END: lick = chordLines ? new Lick(pick(lineEnds, rnd), true) : new Lick(pick(major ? MAJOR_SHORT : BLUES_SHORT, rnd), false); break;
                case RIFF: riffStr = pick(riffs, rnd); riff = new Lick(riffStr, false); lick = riff; break;
                case RIFF_AGAIN: lick = riff != null ? riff : new Lick(pick(riffs, rnd), false); break;
                case RIFF_OTHER: lick = new Lick(pickOther(riffs, riffStr, rnd), false); break;
                case END: lick = new Lick(pick(ends, rnd), false); break;
                default: out.add(new Note(t0, 4, null)); t = t0 + 4; continue;
            }
            t = place(out, lick, t0, style, k, minor, lo, hi, last, part == Part.END, level);
        }
        if (t < bars * 4 - 1e-9) out.add(new Note(t, bars * 4 - t, null));
        return clean(out, bars, lo, hi);
    }

    private static String pick(String[] a, Random rnd) { return a[rnd.nextInt(a.length)]; }

    /** Another entry than [not] (when [not] starts it). */
    private static String pickOther(String[] a, @Nullable String not, Random rnd) {
        for (int i = 0; i < 12; i++) { String s = pick(a, rnd); if (not == null || !not.startsWith(s)) return s; }
        return pick(a, rnd);
    }

    /** The tokens of a lick that fill its first bar (4 beats), padded with a rest. */
    private static String firstBar(String lick) {
        StringBuilder b = new StringBuilder();
        double sum = 0;
        for (String tok : lick.trim().split("\\s+")) {
            double d = Double.parseDouble(tok.split(":")[1]);
            if (sum + d > 4 + 1e-9) break;
            if (b.length() > 0) b.append(' ');
            b.append(tok);
            sum += d;
        }
        if (sum < 4 - 1e-9) b.append(" r:").append(4 - sum);
        return b.toString();
    }

    /**
     * Writes [lick] from beat [t0], each note over the chord of its bar, the whole lick shifted by octaves to start near
     * the previous note and stay in range. Returns the beat after it. [end]: the last note is the key's root.
     */
    private static double place(List<Note> out, Lick lick, double t0, Groove.Style style, int k, boolean minor, int lo, int hi,
                                int[] last, boolean end, Level level) {
        int n = lick.beats.length;
        int[] rel = new int[n];
        double tt = t0;
        int first = Integer.MIN_VALUE, top = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            Groove.Chord ch = style.form.get(((int) Math.floor(tt / 4 + 1e-9)) % style.bars());
            if (lick.value[i] != Integer.MIN_VALUE) {
                rel[i] = semis(lick, i, ch, minor);
                if (first == Integer.MIN_VALUE) first = rel[i];
                top = Math.max(top, rel[i]);
            }
            tt += lick.beats[i];
        }
        // Octave for the whole lick: fits the range, starts close to the last note.
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) if (lick.value[i] != Integer.MIN_VALUE) { min = Math.min(min, rel[i]); max = Math.max(max, rel[i]); }
        int shift = 0;
        if (first != Integer.MIN_VALUE) {
            int best = Integer.MAX_VALUE;
            for (int o = -4; o <= 6; o++) {
                int s = k + 12 * o;
                boolean fits = min + s >= lo && max + s <= hi;
                int mid = (lo + hi) / 2;
                int cost = Math.abs(first + s - last[0]) + Math.abs((min + max) / 2 + s - mid) / 2 + (fits ? 0 : 100);
                if (cost < best) { best = cost; shift = s; }
            }
        }
        boolean swing = style.feel != Groove.Feel.SIXTEENTHS;
        double t = t0;
        int lastIdx = -1;
        for (int i = 0; i < n; i++) if (lick.value[i] != Integer.MIN_VALUE) lastIdx = i;
        for (int i = 0; i < n; i++) {
            double d = lick.beats[i];
            if (lick.value[i] == Integer.MIN_VALUE) { out.add(new Note(t, d, null)); t += d; continue; }
            int midi = rel[i] + shift;
            if (end && i == lastIdx) midi = nearestRoot(midi, k, lo, hi);
            double frac = t - Math.floor(t);
            double vel = 0.85;
            if (swing && Math.abs(frac - 0.5) < 1e-9 && d <= 0.5) vel = 1.0;         // swing: accent the off-beats
            if (rel[i] == top) vel += 0.1;                                             // the peak of the phrase
            if (i == lastIdx) vel -= 0.1;                                              // phrase endings relax
            if (level == Level.EASY) vel = 0.9;
            out.add(new Note(t, d, midi, vel));
            last[0] = midi;
            t += d;
        }
        return t;
    }

    private static int nearestRoot(int midi, int k, int lo, int hi) {
        int best = midi, bd = 99;
        for (int m = lo; m <= hi; m++) if (Math.floorMod(m - k, 12) == 0 && Math.abs(m - midi) < bd) { bd = Math.abs(m - midi); best = m; }
        return best;
    }

    /** Cuts what runs past the solo, joins overlaps, and brings any note outside the range back an octave. */
    private static List<Note> clean(List<Note> in, int bars, int lo, int hi) {
        in.sort((a, b) -> Double.compare(a.start, b.start));
        List<Note> out = new ArrayList<>();
        for (Note n : in) {
            if (n.start >= bars * 4 - 1e-9) continue;
            double len = Math.min(n.beats, bars * 4 - n.start);
            Integer m = n.midi;
            if (m != null) { while (m < lo) m += 12; while (m > hi) m -= 12; }
            if (!out.isEmpty()) {
                Note p = out.get(out.size() - 1);
                if (p.start + p.beats > n.start + 1e-9) {
                    out.remove(out.size() - 1);
                    if (n.start - p.start > 1e-9) out.add(new Note(p.start, n.start - p.start, p.midi, p.vel));
                }
            }
            out.add(new Note(n.start, len, m, n.vel));
        }
        return out;
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
