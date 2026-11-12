package app.supersax.domain.solo;

import app.supersax.domain.pitch.PitchClass;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Backing tracks for solo practice: chord forms for several styles, arranged over several choruses (walking bass
 * and comping that vary, a drum fill at the end of each chorus) and played by recorded instruments (a [Band]),
 * or by synthesis when no sound file is available. Each part is kept in its own buffer so it can be mixed.
 */
public final class Groove {
    private Groove() {}

    /** Chord quality: suffix shown after the root, and the chord tones (letter steps, semitones). */
    public enum Quality {
        DOM7("7", new int[][]{{0, 0}, {2, 4}, {4, 7}, {6, 10}}),
        MIN7("m7", new int[][]{{0, 0}, {2, 3}, {4, 7}, {6, 10}}),
        MAJ7("maj7", new int[][]{{0, 0}, {2, 4}, {4, 7}, {6, 11}});
        public final String suffix;
        final int[][] tones;
        Quality(String suffix, int[][] tones) { this.suffix = suffix; this.tones = tones; }
    }

    /** A chord of the form: its root above the key (letter steps, semitones) and its quality. */
    public static final class Chord {
        public final int steps, semitones;
        public final Quality quality;
        Chord(int steps, int semitones, Quality quality) { this.steps = steps; this.semitones = semitones; this.quality = quality; }
        public PitchClass root(PitchClass key) { return Blues.above(key, steps, semitones); }
        public List<PitchClass> tones(PitchClass key) {
            PitchClass r = root(key);
            List<PitchClass> out = new ArrayList<>();
            for (int[] t : quality.tones) out.add(Blues.above(r, t[0], t[1]));
            return out;
        }
        /** The chord's 3rd: the note that tells major from minor. */
        public PitchClass third(PitchClass key) { return tones(key).get(1); }
    }

    public enum Feel { SHUFFLE, SWING, TRIPLET, SIXTEENTHS }

    private static Chord c(int steps, int semis, Quality q) { return new Chord(steps, semis, q); }
    private static final Chord I7 = c(0, 0, Quality.DOM7), IV7 = c(3, 5, Quality.DOM7), V7 = c(4, 7, Quality.DOM7);
    private static final Chord Im = c(0, 0, Quality.MIN7), IVm = c(3, 5, Quality.MIN7), BVI7 = c(5, 8, Quality.DOM7);
    private static final Chord IIm = c(1, 2, Quality.MIN7), IMAJ = c(0, 0, Quality.MAJ7);

    /** The styles, with their form, feel and usual tempo. */
    public enum Style {
        BLUES(Feel.SHUFFLE, 80, false, Arrays.asList(I7, I7, I7, I7, IV7, IV7, I7, I7, V7, IV7, I7, V7)),
        MINOR_BLUES(Feel.SHUFFLE, 76, true, Arrays.asList(Im, Im, Im, Im, IVm, IVm, Im, Im, BVI7, V7, Im, V7)),
        SLOW_BLUES(Feel.TRIPLET, 56, false, Arrays.asList(I7, IV7, I7, I7, IV7, IV7, I7, I7, V7, IV7, I7, V7)),
        FUNK(Feel.SIXTEENTHS, 96, false, Arrays.asList(I7, I7, I7, I7, IV7, IV7, I7, I7)),
        JAZZ_251(Feel.SWING, 120, false, Arrays.asList(IIm, V7, IMAJ, IMAJ));
        public final Feel feel;
        public final int defaultBpm;
        public final boolean minor;
        public final List<Chord> form;
        Style(Feel feel, int bpm, boolean minor, List<Chord> form) { this.feel = feel; this.defaultBpm = bpm; this.minor = minor; this.form = form; }
        public int bars() { return form.size(); }
    }

    /** Seconds of one chorus of the form (4 beats per bar; in 12/8 a beat is a dotted quarter). */
    public static double loopSeconds(Style style, double bpm) { return style.bars() * 4 * 60.0 / bpm; }

    /** Choruses in one rendered loop, so the parts can vary from one chorus to the next (at least 16 bars). */
    public static int choruses(Style style) { return Math.max(2, (16 + style.bars() - 1) / style.bars()); }

    /** The parts of a rendered loop, mixed separately so each can be turned up or down. */
    public enum Part { DRUMS, BASS, KEYS, LEAD }

    /** A rendered loop: one buffer per part, at [rate]. */
    public static final class Mix {
        public final float[][] stems;
        public final int rate;
        Mix(float[][] stems, int rate) { this.stems = stems; this.rate = rate; }
        public int frames() { return stems[0].length; }
        /** All parts summed with [gains] (one per Part), through a gentle limiter. */
        public float[] mix(float[] gains) {
            float[] out = new float[frames()];
            mixInto(out, 0, out.length, 0, gains);
            return out;
        }
        /** Mixes frames [from, from+n) of the loop into [out] at [at]. */
        public void mixInto(float[] out, int at, int n, int from, float[] gains) {
            for (int i = 0; i < n; i++) {
                int k = from + i;
                float v = 0;
                for (int p = 0; p < stems.length; p++) v += stems[p][k] * gains[p];
                out[at + i] = (float) Math.tanh(v * 1.1) * 0.92f;
            }
        }
    }

    // --- rendering ------------------------------------------------------------------------------

    /** Synthesised loop, all parts at full volume (kept for callers that want one buffer). */
    public static float[] render(Style style, int bassMidi, double bpm, int rate, @Nullable List<List<Blues.PhraseNote>> calls, int leadMidi) {
        return render(style, bassMidi, bpm, rate, calls, leadMidi, null).mix(new float[]{1, 1, 1, 1});
    }

    /**
     * One loop ([choruses] times the form) of [style] in the concert key whose bass root is [bassMidi]
     * (e.g. 46 = B♭2), at [bpm]. [calls]: 2-bar phrases played by a lead voice at bars 0, 4, 8… of each chorus
     * (null: no lead), around [leadMidi]. With a [band], recorded instruments play; without, synthesis.
     */
    public static Mix render(Style style, int bassMidi, double bpm, int rate, @Nullable List<List<Blues.PhraseNote>> calls, int leadMidi,
                             @Nullable app.supersax.domain.audio.Band band) {
        return render(style, bassMidi, bpm, rate, calls, leadMidi, band, null);
    }

    /** As above; a generated [solo] (beats from the loop start, concert pitch) is played by the lead instead of calls. */
    public static Mix render(Style style, int bassMidi, double bpm, int rate, @Nullable List<List<Blues.PhraseNote>> calls, int leadMidi,
                             @Nullable app.supersax.domain.audio.Band band, @Nullable List<SoloGenerator.Note> solo) {
        double beat = 60.0 / bpm;
        int choruses = choruses(style);
        int n = (int) Math.round(loopSeconds(style, bpm) * choruses * rate);
        float[][] stems = new float[Part.values().length][n];
        Random rnd = new Random(11);
        Voices v = band == null ? new Synth(stems, rate, rnd) : new Sampled(stems, rate, rnd, band, style);
        Arranger a = new Arranger(style, bassMidi, beat, rnd);
        int total = style.bars() * choruses;
        for (int bar = 0; bar < total; bar++) {
            int inForm = bar % style.bars();
            int chorus = bar / style.bars();
            Chord ch = style.form.get(inForm);
            Chord next = style.form.get((inForm + 1) % style.bars());
            double t0 = bar * 4 * beat;
            boolean last = inForm == style.bars() - 1;
            a.drums(v, t0, bar, chorus, inForm, last);
            a.bass(v, ch, next, t0, chorus);
            a.comp(v, ch, t0, chorus, inForm);
        }
        if (solo != null) {
            for (SoloGenerator.Note sn : solo) {
                if (sn.midi == null) continue;
                double frac = sn.start - Math.floor(sn.start);
                double at = Math.abs(frac - 0.5) < 1e-9 ? Math.floor(sn.start) + swingOffset(style.feel) : sn.start;
                double len = sn.beats;
                if (Math.abs(frac) < 1e-9 && Math.abs(len - 0.5) < 1e-9) len = swingOffset(style.feel); // long-short eighths
                v.lead(at * beat, Math.max(0.08, len * beat * 0.95), sn.midi);
            }
        } else if (calls != null && !calls.isEmpty()) {
            for (int c = 0; c < choruses; c++) {
                for (int i = 0; i * 4 < style.bars(); i++) {
                    for (Blues.PhraseNote p : calls.get(i % calls.size())) {
                        double whole = c * style.bars() * 4 + i * 16 + p.getStartBeat();
                        double frac = whole - Math.floor(whole);
                        double off = frac >= 0.5 ? swingOffset(style.feel) : 0;
                        v.lead((Math.floor(whole) + off) * beat, p.getBeats() * beat * 0.92, leadMidi + p.getSemitones());
                    }
                }
            }
        }
        // Balance: each part to a set loudness (whatever the instruments' recording levels), lead a little forward.
        double[] target = {0.040, 0.050, 0.038, 0.050};
        for (int p = 0; p < stems.length; p++) {
            double sum = 0;
            for (float x : stems[p]) sum += x * x;
            double rms = Math.sqrt(sum / n);
            if (rms > 1e-6) { float g = (float) (target[p] / rms); for (int i = 0; i < n; i++) stems[p][i] *= g; }
        }
        // Common level so the loudest moment of the full mix sits near 0.8.
        float peak = 0;
        for (int i = 0; i < n; i++) {
            float s = 0;
            for (float[] st : stems) s += st[i];
            peak = Math.max(peak, Math.abs(s));
        }
        if (peak > 0) {
            float g = 0.8f / peak;
            for (float[] st : stems) for (int i = 0; i < n; i++) st[i] *= g;
        }
        return new Mix(stems, rate);
    }

    /** Where the off-beat eighth falls inside a beat. */
    static double swingOffset(Feel f) {
        switch (f) {
            case SHUFFLE: case SWING: case TRIPLET: return 2.0 / 3.0;
            default: return 0.5;
        }
    }

    static double hz(double midi) { return 440.0 * Math.pow(2, (midi - 69) / 12.0); }

    /** What every instrument set can play. Times in seconds, pitches in MIDI, loudness 0..1. */
    interface Voices {
        void kick(double t, double v);
        void snare(double t, double v);
        void sideStick(double t, double v);
        void brush(double t, double v);
        void hat(double t, double v, boolean open);
        void pedalHat(double t, double v);
        void ride(double t, double v);
        void crash(double t, double v);
        void tom(double t, int which, double v);
        void bass(double t, double dur, double midi, double v);
        void chord(double t, double dur, int[] notes, double v);
        void lead(double t, double dur, double midi);
    }

    /** Writes the parts: grooves per feel, with variations from one chorus to the next and a fill to close each chorus. */
    static final class Arranger {
        final Style style;
        final int bassMidi;
        final double beat;
        final Random rnd;
        int[] lastVoicing;
        int lastBass = -1;

        Arranger(Style style, int bassMidi, double beat, Random rnd) { this.style = style; this.bassMidi = bassMidi; this.beat = beat; this.rnd = rnd; }

        int rootOf(Chord ch) {
            int r = bassMidi + ch.semitones;
            if (r > bassMidi + 7) r -= 12;
            return r;
        }

        void drums(Voices s, double t0, int bar, int chorus, int inForm, boolean last) {
            if (inForm == 0) s.crash(t0, chorus == 0 ? 0.45 : 0.7);
            boolean fill = last;
            switch (style.feel) {
                case SHUFFLE:
                    for (int b = 0; b < 4; b++) {
                        double t = t0 + b * beat;
                        if (fill && b >= 2) continue;
                        if (b % 2 == 0) s.kick(t, 0.85); else s.snare(t, 0.7);
                        if (chorus % 2 == 0) { s.hat(t, 0.55, false); s.hat(t + beat * 2 / 3, 0.35, false); }
                        else { s.ride(t, 0.55); s.ride(t + beat * 2 / 3, 0.35); }
                    }
                    if (fill) tripletFill(s, t0 + 2 * beat);
                    break;
                case SWING:
                    for (int b = 0; b < 4; b++) {
                        double t = t0 + b * beat;
                        s.ride(t, b % 2 == 0 ? 0.55 : 0.65);
                        if (b % 2 == 1) { s.ride(t + beat * 2 / 3, 0.4); s.pedalHat(t, 0.5); }
                        s.kick(t, 0.18);
                    }
                    // Comping on the snare: a few soft off-beat hits, different each bar.
                    for (int k = 0; k < 2; k++) if (rnd.nextDouble() < 0.4) s.snare(t0 + (rnd.nextInt(4) + 2.0 / 3) * beat, 0.25);
                    if (fill) { s.snare(t0 + 3 * beat, 0.45); s.snare(t0 + 3 * beat + beat * 2 / 3, 0.55); s.kick(t0 + 3 * beat + beat * 2 / 3, 0.5); }
                    break;
                case TRIPLET:
                    for (int b = 0; b < 4; b++) {
                        double t = t0 + b * beat;
                        if (fill && b == 3) continue;
                        if (b % 2 == 0) s.kick(t, 0.8); else s.brush(t, 0.85);
                        for (int k = 0; k < 3; k++) s.hat(t + k * beat / 3, k == 0 ? 0.45 : 0.28, false);
                    }
                    if (fill) for (int k = 0; k < 3; k++) s.tom(t0 + 3 * beat + k * beat / 3, 2 - k, 0.5 + 0.1 * k);
                    break;
                case SIXTEENTHS: {
                    double q = beat / 4;
                    s.kick(t0, 1.0);
                    s.kick(t0 + 1 * beat + 3 * q, 0.7);
                    s.kick(t0 + 2 * beat + (chorus % 2 == 0 ? 2 : 1) * q, 0.85);
                    s.snare(t0 + beat, 0.85);
                    if (!fill) s.snare(t0 + 3 * beat, 0.9);
                    s.snare(t0 + 2 * beat + 3 * q, 0.18);
                    if (rnd.nextDouble() < 0.6) s.snare(t0 + 3 * beat + 2 * q, 0.16);
                    for (int k = 0; k < (fill ? 12 : 16); k++) s.hat(t0 + k * q, k % 2 == 0 ? 0.5 : 0.25, k == 14 || (k == 6 && inForm % 2 == 1));
                    if (fill) for (int k = 12; k < 16; k++) s.snare(t0 + k * q, 0.45 + 0.12 * (k - 12));
                    break;
                }
            }
        }

        private void tripletFill(Voices s, double t) {
            for (int k = 0; k < 6; k++) {
                double at = t + k * beat / 3;
                if (k < 3) s.snare(at, 0.4 + 0.08 * k); else s.tom(at, 5 - k, 0.6);
            }
        }

        void bass(Voices s, Chord ch, Chord next, double t0, int chorus) {
            int root = rootOf(ch), nextRoot = rootOf(next);
            switch (style.feel) {
                case SHUFFLE: {
                    int[][] lines = ch.quality == Quality.MIN7
                        ? new int[][]{{0, 3, 7, 10}, {0, 7, 10, 7}, {0, 3, 5, 6}}
                        : new int[][]{{0, 4, 7, 9}, {0, 4, 7, 10}, {0, 7, 9, 10}, {12, 10, 9, 7}};
                    int[] line = lines[chorus == 0 ? 0 : rnd.nextInt(lines.length)];
                    for (int b = 0; b < 4; b++) {
                        s.bass(t0 + b * beat, beat * 0.85, root + line[b], 0.9);
                        if (chorus > 0 && b == 3 && rnd.nextDouble() < 0.3) s.bass(t0 + b * beat + beat * 2 / 3, beat * 0.3, root + line[b] - 1, 0.6);
                    }
                    break;
                }
                case TRIPLET:
                    s.bass(t0, beat * 1.9, root, 0.95);
                    s.bass(t0 + beat * 2 - beat / 3, beat / 3 * 0.9, root + (chorus % 2 == 0 ? 5 : 4), 0.6);
                    s.bass(t0 + beat * 2, beat * 1.3, root + 7, 0.85);
                    s.bass(t0 + beat * 3 + beat * 2 / 3, beat / 3, nextRoot + (nextRoot > root + 7 ? -1 : 1), 0.6);
                    break;
                case SIXTEENTHS: {
                    double q = beat / 4;
                    s.bass(t0, q * 1.6, root, 1.0);
                    s.bass(t0 + 3 * q, q * 0.8, root + 12, 0.7);
                    s.bass(t0 + beat + 2 * q, q * 1.5, root, 0.85);
                    if (chorus % 2 == 0) {
                        s.bass(t0 + 2 * beat + 2 * q, q * 0.9, root + 10, 0.8);
                        s.bass(t0 + 3 * beat + q, q * 0.9, root + 7, 0.75);
                        s.bass(t0 + 3 * beat + 3 * q, q * 0.8, root + 5, 0.6);
                    } else {
                        s.bass(t0 + 2 * beat + 2 * q, q * 0.9, root + 7, 0.8);
                        s.bass(t0 + 3 * beat, q * 0.9, root + 10, 0.75);
                        s.bass(t0 + 3 * beat + 2 * q, q * 0.8, root + 12, 0.7);
                    }
                    break;
                }
                case SWING: {
                    // Walking: root on 1, chord or scale tones on 2 and 3, and an approach note into the next root on 4.
                    int third = root + ch.quality.tones[1][1], fifth = root + 7, seventh = root + ch.quality.tones[3][1];
                    int[][] middles = {{third, fifth}, {fifth, third}, {root + 2, third}, {fifth, seventh - 12 >= bassMidi - 7 ? seventh - 12 : seventh}, {third, root + 5}};
                    int[] mid = middles[rnd.nextInt(middles.length)];
                    int target = nextRoot;
                    int approach;
                    double r = rnd.nextDouble();
                    if (r < 0.45) approach = target + (mid[1] > target ? 1 : -1);      // half step
                    else if (r < 0.75) approach = target + 7 > bassMidi + 9 ? target - 5 : target + 7; // the 5th of the next chord
                    else approach = target + (mid[1] > target ? 2 : -2);               // whole step
                    int[] line = {root, mid[0], mid[1], approach};
                    for (int b = 0; b < 4; b++) s.bass(t0 + b * beat, beat * 0.92, line[b], b == 0 ? 0.9 : 0.8);
                    if (rnd.nextDouble() < 0.12) s.bass(t0 + 2 * beat + beat * 2 / 3, beat * 0.25, mid[1], 0.45); // a skip note
                    break;
                }
            }
        }

        /** Rootless voicing of [ch] near the previous one (smooth voice leading). */
        int[] voicing(Chord ch) {
            int base = bassMidi + 12 + ch.semitones;
            while (base < 50) base += 12;
            while (base > 61) base -= 12;
            int[] a = {base + ch.quality.tones[1][1], base + ch.quality.tones[2][1], base + ch.quality.tones[3][1], base + 14};
            int[] b = {base + ch.quality.tones[3][1] - 12, base + 2, base + ch.quality.tones[1][1], base + ch.quality.tones[2][1]};
            if (b[0] < 48) for (int i = 0; i < 4; i++) b[i] += 12;
            int[] pick = a;
            if (lastVoicing != null && dist(b, lastVoicing) < dist(a, lastVoicing)) pick = b;
            Arrays.sort(pick);
            lastVoicing = pick;
            return pick;
        }

        private static int dist(int[] x, int[] y) {
            int d = 0;
            for (int i = 0; i < x.length; i++) d += Math.abs(x[i] - y[i]);
            return d;
        }

        void comp(Voices s, Chord ch, double t0, int chorus, int inForm) {
            int[] v = voicing(ch);
            double sw = beat * 2 / 3;
            switch (style.feel) {
                case SHUFFLE: {
                    double[][] patterns = {{1 + 2.0 / 3, 3 + 2.0 / 3}, {1, 3}, {0, 1 + 2.0 / 3, 2 + 2.0 / 3}, {0 + 2.0 / 3, 2}};
                    double[] p = patterns[chorus == 0 && inForm < 4 ? 0 : rnd.nextInt(patterns.length)];
                    for (double b : p) s.chord(t0 + b * beat, beat * 0.4, v, 0.5);
                    break;
                }
                case SWING: {
                    // Charleston, anticipation of the next bar, "Red Garland" 2 and 4, or a long chord on 1.
                    switch (rnd.nextInt(4)) {
                        case 0: s.chord(t0, beat * 0.6, v, 0.55); s.chord(t0 + beat + sw, beat * 0.4, v, 0.5); break;
                        case 1: s.chord(t0 + 3 * beat + sw, beat * 0.9, v, 0.5); break;
                        case 2: s.chord(t0 + beat, beat * 0.35, v, 0.5); s.chord(t0 + 3 * beat, beat * 0.35, v, 0.5); break;
                        default: s.chord(t0, beat * 1.8, v, 0.45); s.chord(t0 + 2 * beat + sw, beat * 0.3, v, 0.4); break;
                    }
                    break;
                }
                case TRIPLET:
                    s.chord(t0, beat * 1.8, v, 0.45);
                    for (int b = 1; b < 4; b++) if (b != 2 || chorus % 2 == 1) s.chord(t0 + b * beat + sw, beat / 3, v, 0.28);
                    if (chorus % 2 == 0) s.chord(t0 + 2 * beat, beat * 1.5, v, 0.35);
                    break;
                case SIXTEENTHS: {
                    double q = beat / 4;
                    double[][] patterns = {{beat + 2 * q, 2 * beat + 3 * q, 3 * beat + 2 * q}, {2 * q, beat + 2 * q, 3 * beat}, {beat + 2 * q, 2 * beat + 2 * q, 3 * beat + 3 * q}};
                    for (double off : patterns[(chorus + inForm) % patterns.length]) s.chord(t0 + off, q * 0.8, v, 0.5);
                    break;
                }
            }
        }
    }

    /** Recorded instruments from the sound files. */
    static final class Sampled implements Voices {
        final float[][] stems;
        final int rate;
        final Random rnd;
        final app.supersax.domain.audio.SampledInstrument keys, bassI, kit, lead;
        final Synth synth;

        Sampled(float[][] stems, int rate, Random rnd, app.supersax.domain.audio.Band b, Style style) {
            this.stems = stems; this.rate = rate; this.rnd = rnd;
            this.synth = new Synth(stems, rate, rnd);
            boolean funk = style == Style.FUNK;
            keys = funk ? first(b.epiano, b.piano) : first(b.piano, b.epiano);
            bassI = funk ? first(b.ebass, b.bass) : first(b.bass, b.ebass);
            kit = funk ? first(b.funkKit, b.jazzKit) : style == Style.SLOW_BLUES ? first(b.brushKit, b.jazzKit) : first(b.jazzKit, b.funkKit);
            lead = b.altoSax;
        }

        private static app.supersax.domain.audio.SampledInstrument first(app.supersax.domain.audio.SampledInstrument a, app.supersax.domain.audio.SampledInstrument b) { return a != null ? a : b; }

        private double human(double t) { return t + (rnd.nextGaussian() * 0.004); }
        private double vel(double v) { return v * (0.88 + 0.24 * rnd.nextDouble()); }
        private int start(double t) { return (int) Math.round(Math.max(0, human(t)) * rate); }

        private boolean drum(int key, double t, double v, double gain) {
            if (kit == null || !kit.has(key)) return false;
            double g = vel(v);
            kit.play(stems[Part.DRUMS.ordinal()], rate, start(t), key, gain * g * g, 3.0, 0.05);
            return true;
        }

        @Override public void kick(double t, double v) { if (!drum(36, t, v, 1.0)) synth.kick(t, v); }
        @Override public void snare(double t, double v) { if (!drum(38, t, v, 0.8)) synth.snare(t, v); }
        @Override public void sideStick(double t, double v) { if (!drum(37, t, v, 0.7)) synth.sideStick(t, v); }
        @Override public void brush(double t, double v) { if (!drum(40, t, v, 0.9) && !drum(38, t, v, 0.8)) synth.brush(t, v); }
        @Override public void hat(double t, double v, boolean open) { if (!drum(open ? 46 : 42, t, v, 0.55)) synth.hat(t, v, open); }
        @Override public void pedalHat(double t, double v) { if (!drum(44, t, v, 0.55)) synth.pedalHat(t, v); }
        @Override public void ride(double t, double v) { if (!drum(51, t, v, 0.5) && !drum(59, t, v, 0.5)) synth.ride(t, v); }
        @Override public void crash(double t, double v) { if (!drum(49, t, v, 0.45)) synth.crash(t, v); }
        @Override public void tom(double t, int which, double v) {
            int[] toms = {41, 43, 45, 47};
            if (!drum(toms[Math.max(0, Math.min(3, which))], t, v, 0.75)) synth.tom(t, which, v);
        }

        @Override public void bass(double t, double dur, double midi, double v) {
            if (bassI == null) { synth.bass(t, dur, midi, v); return; }
            double g = vel(v);
            bassI.play(stems[Part.BASS.ordinal()], rate, start(t), midi, 0.9 * g, dur, 0.06);
        }

        @Override public void chord(double t, double dur, int[] notes, double v) {
            if (keys == null) { synth.chord(t, dur, notes, v); return; }
            double strum = 0;
            double g = vel(v);
            for (int m : notes) {
                keys.play(stems[Part.KEYS.ordinal()], rate, start(t + strum), m, 0.32 * g, dur, 0.12);
                strum += 0.005;
            }
        }

        @Override public void lead(double t, double dur, double midi) {
            if (lead == null) { synth.lead(t, dur, midi); return; }
            lead.play(stems[Part.LEAD.ordinal()], rate, (int) Math.round(t * rate), midi, 0.55, dur, 0.06);
        }
    }

    /** The instruments. Each event gets a few milliseconds of human timing and a little loudness variation. */
    static final class Synth implements Voices {
        final float[][] stems;
        final int rate;
        final Random rnd;
        float[] out;
        Synth(float[][] stems, int rate, Random rnd) { this.stems = stems; this.rate = rate; this.rnd = rnd; this.out = stems[0]; }
        private void to(Part p) { out = stems[p.ordinal()]; }

        private double human(double t) { return t + (rnd.nextGaussian() * 0.004); }
        private double vel(double v) { return v * (0.88 + 0.24 * rnd.nextDouble()); }
        private int start(double t) { return (int) Math.round(Math.max(0, human(t)) * rate); }

        /** Plucked string (Karplus–Strong) with a little sine body, for the bass. */
        @Override public void bass(double t, double dur, double midi, double v) {
            to(Part.BASS);
            double f = hz(midi);
            int s0 = start(t), len = (int) (Math.min(dur + 0.08, 2.5) * rate);
            int period = Math.max(2, (int) Math.round(rate / f));
            double[] buf = new double[period];
            double lp = 0;
            for (int i = 0; i < period; i++) { lp += 0.35 * ((rnd.nextDouble() * 2 - 1) - lp); buf[i] = lp; }
            double a = 0.32 * vel(v), prev = 0;
            int releaseAt = (int) (dur * rate);
            for (int i = 0; i < len && s0 + i < out.length; i++) {
                int k = i % period;
                double y = buf[k];
                double next = 0.5 * (y + buf[(k + 1) % period]) * 0.9985;
                buf[k] = next;
                double x = (double) i / rate;
                double body = 0.55 * Math.sin(2 * Math.PI * f * x) * Math.exp(-x / 0.9);
                double env = i < releaseAt ? 1 : Math.exp(-(i - releaseAt) / (0.03 * rate));
                prev += 0.5 * ((y * 2.2 + body) - prev); // soften the attack
                out[s0 + i] += (float) (a * env * prev);
            }
        }

        /** FM electric piano note: a bell-like attack that mellows quickly. */
        void piano(double t, double dur, double midi, double v) {
            to(Part.KEYS);
            double f = hz(midi);
            int s0 = start(t), len = (int) ((dur + 0.25) * rate);
            double a = 0.11 * vel(v);
            int releaseAt = (int) (dur * rate);
            for (int i = 0; i < len && s0 + i < out.length; i++) {
                double x = (double) i / rate;
                double index = 1.8 * Math.exp(-x / 0.35) + 0.25;
                double mod = Math.sin(2 * Math.PI * f * x);
                double tine = 0.18 * Math.sin(2 * Math.PI * f * 7.0 * x) * Math.exp(-x / 0.03);
                double env = Math.min(1, x / 0.004) * Math.exp(-x / 1.6) * (i < releaseAt ? 1 : Math.exp(-(i - releaseAt) / (0.08 * rate)));
                out[s0 + i] += (float) (a * env * (Math.sin(2 * Math.PI * f * x + index * mod) + tine));
            }
        }

        @Override public void chord(double t, double dur, int[] notes, double v) {
            double strum = 0;
            for (int m : notes) { piano(t + strum, dur, m, v); strum += 0.006; }
        }

        @Override public void kick(double t, double v) {
            to(Part.DRUMS);
            int s0 = start(t), len = (int) (0.35 * rate);
            double a = 0.75 * vel(v), phase = 0;
            for (int i = 0; i < len && s0 + i < out.length; i++) {
                double x = (double) i / rate;
                phase += 2 * Math.PI * (48 + 110 * Math.exp(-x / 0.025)) / rate;
                double click = i < rate * 0.002 ? (rnd.nextDouble() * 2 - 1) * 0.3 : 0;
                out[s0 + i] += (float) (a * (Math.exp(-x / 0.11) * Math.sin(phase) + click));
            }
        }

        @Override public void snare(double t, double v) {
            to(Part.DRUMS);
            int s0 = start(t), len = (int) (0.25 * rate);
            double a = 0.32 * vel(v);
            double b1 = 0, b2 = 0; // two-pole band-pass state around 3.5 kHz
            double w = 2 * Math.PI * Math.min(3500, rate * 0.4) / rate, r = 0.85;
            for (int i = 0; i < len && s0 + i < out.length; i++) {
                double x = (double) i / rate;
                double noise = rnd.nextDouble() * 2 - 1;
                double bp = noise + 2 * r * Math.cos(w) * b1 - r * r * b2;
                b2 = b1; b1 = bp;
                double body = Math.sin(2 * Math.PI * 185 * x) * Math.exp(-x / 0.05) + 0.5 * Math.sin(2 * Math.PI * 330 * x) * Math.exp(-x / 0.03);
                out[s0 + i] += (float) (a * (0.55 * body + 0.18 * bp * Math.exp(-x / 0.12)));
            }
        }

        /** Brush on the snare: a soft swish. */
        @Override public void brush(double t, double v) {
            to(Part.DRUMS);
            int s0 = start(t - 0.02), len = (int) (0.35 * rate);
            double a = 0.16 * vel(v), lp = 0;
            for (int i = 0; i < len && s0 + i < out.length; i++) {
                double x = (double) i / rate;
                lp += 0.25 * ((rnd.nextDouble() * 2 - 1) - lp);
                double env = Math.min(1, x / 0.03) * Math.exp(-x / 0.12);
                out[s0 + i] += (float) (a * env * lp);
            }
        }

        private static final double[] METAL = {205.3, 304.4, 369.6, 522.7, 540.0, 800.0};

        /** Metallic noise: six detuned square waves, high-passed. */
        private void metal(double t, double v, double decay, double amp, double scale) {
            to(Part.DRUMS);
            int s0 = start(t), len = (int) (decay * 6 * rate);
            double a = amp * vel(v), prev = 0, hp = 0;
            for (int i = 0; i < len && s0 + i < out.length; i++) {
                double x = (double) i / rate;
                double sum = 0;
                for (double f : METAL) sum += Math.signum(Math.sin(2 * Math.PI * f * scale * x));
                hp = 0.7 * (hp + sum - prev);
                prev = sum;
                out[s0 + i] += (float) (a * Math.exp(-x / decay) * hp / 6);
            }
        }

        @Override public void hat(double t, double v, boolean open) { metal(t, v, open ? 0.12 : 0.025, 0.12, 8); }
        @Override public void pedalHat(double t, double v) { metal(t, v, 0.02, 0.08, 8); }
        @Override public void ride(double t, double v) { metal(t, v, 0.45, 0.06, 5.5); }
        @Override public void crash(double t, double v) { metal(t, v, 0.6, 0.07, 7); }
        @Override public void sideStick(double t, double v) { metal(t, v, 0.015, 0.2, 3); }
        @Override public void tom(double t, int which, double v) {
            to(Part.DRUMS);
            int s0 = start(t), len = (int) (0.3 * rate);
            double a = 0.5 * vel(v), phase = 0, f0 = 90 + 35 * which;
            for (int i = 0; i < len && s0 + i < out.length; i++) {
                double x = (double) i / rate;
                phase += 2 * Math.PI * (f0 * (1 + 0.5 * Math.exp(-x / 0.03))) / rate;
                out[s0 + i] += (float) (a * Math.exp(-x / 0.12) * Math.sin(phase));
            }
        }

        /** Reedy lead voice with vibrato and a little breath, for the call phrases. */
        @Override public void lead(double t, double dur, double midi) {
            to(Part.LEAD);
            double f = hz(midi);
            int s0 = (int) (t * rate), len = (int) (dur * rate);
            double phase = 0, lp = 0;
            for (int i = 0; i < len && s0 + i < out.length; i++) {
                double x = (double) i / rate;
                double env = Math.min(1, x / 0.03) * Math.min(1, (len - i) / (0.04 * rate)) * (0.85 + 0.15 * Math.exp(-x / 0.2));
                double fr = f * (1 + 0.004 * Math.sin(2 * Math.PI * 5.5 * x) * Math.min(1, x / 0.25));
                phase += 2 * Math.PI * fr / rate;
                lp += 0.2 * ((rnd.nextDouble() * 2 - 1) - lp);
                double v = Math.sin(phase) + 0.45 * Math.sin(2 * phase) + 0.3 * Math.sin(3 * phase) + 0.15 * Math.sin(4 * phase) + 0.06 * lp;
                out[s0 + i] += (float) (0.16 * env * v);
            }
        }
    }
}
