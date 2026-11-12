package app.supersax.domain.solo;

import app.supersax.domain.pitch.PitchClass;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Backing tracks for solo practice, synthesised: chord forms for several styles, and one loop rendered
 * with a plucked-string bass (Karplus–Strong), an FM electric piano, synthesised drums (kick, snare,
 * brushes, metallic hi-hat and ride) and small human variations in timing and loudness.
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

    /** Seconds of one loop of the form (4 beats per bar; in 12/8 a beat is a dotted quarter). */
    public static double loopSeconds(Style style, double bpm) { return style.bars() * 4 * 60.0 / bpm; }

    // --- rendering ------------------------------------------------------------------------------

    /**
     * One loop of [style] in the concert key whose bass root is [bassMidi] (e.g. 46 = B♭2), at [bpm].
     * [calls]: 2-bar phrases played by a lead voice at bars 0, 4, 8… (null: no lead), around [leadMidi].
     */
    public static float[] render(Style style, int bassMidi, double bpm, int rate, @Nullable List<List<Blues.PhraseNote>> calls, int leadMidi) {
        double beat = 60.0 / bpm;
        int n = (int) Math.round(loopSeconds(style, bpm) * rate);
        float[] out = new float[n];
        Random rnd = new Random(11);
        Synth s = new Synth(out, rate, rnd);
        for (int bar = 0; bar < style.bars(); bar++) {
            Chord ch = style.form.get(bar);
            Chord next = style.form.get((bar + 1) % style.bars());
            double t0 = bar * 4 * beat;
            drums(s, style, t0, beat, bar);
            bass(s, style, ch, next, bassMidi, t0, beat);
            comp(s, style, ch, bassMidi, t0, beat, bar);
        }
        if (calls != null) {
            for (int i = 0; i < calls.size() && i * 4 < style.bars(); i++) {
                for (Blues.PhraseNote p : calls.get(i)) {
                    double whole = i * 16 + p.getStartBeat();
                    double frac = whole - Math.floor(whole);
                    double off = frac >= 0.5 ? swingOffset(style.feel) : 0;
                    s.lead((Math.floor(whole) + off) * beat, p.getBeats() * beat * 0.92, hz(leadMidi + p.getSemitones()));
                }
            }
        }
        // Gentle limiter.
        float peak = 0;
        for (float v : out) peak = Math.max(peak, Math.abs(v));
        float g = peak > 0.85f ? 0.85f / peak : 1f;
        for (int i = 0; i < n; i++) out[i] = (float) Math.tanh(out[i] * g * 1.15) * 0.9f;
        return out;
    }

    /** Where the off-beat eighth falls inside a beat. */
    static double swingOffset(Feel f) {
        switch (f) {
            case SHUFFLE: case SWING: case TRIPLET: return 2.0 / 3.0;
            default: return 0.5;
        }
    }

    static double hz(double midi) { return 440.0 * Math.pow(2, (midi - 69) / 12.0); }

    private static void drums(Synth s, Style style, double t0, double beat, int bar) {
        switch (style.feel) {
            case SHUFFLE:
                for (int b = 0; b < 4; b++) {
                    double t = t0 + b * beat;
                    if (b % 2 == 0) s.kick(t, 0.9); else s.snare(t, 0.75);
                    s.hat(t, 0.55, false);
                    s.hat(t + beat * 2 / 3, 0.35, false);
                }
                break;
            case SWING:
                // Ride "ding, ding-da, ding, ding-da", hi-hat pedal on 2 and 4, feathered kick.
                for (int b = 0; b < 4; b++) {
                    double t = t0 + b * beat;
                    s.ride(t, b % 2 == 0 ? 0.55 : 0.65);
                    if (b % 2 == 1) { s.ride(t + beat * 2 / 3, 0.4); s.hat(t, 0.45, false); }
                    s.kick(t, 0.25);
                }
                if (bar % 4 == 3) s.snare(t0 + 3 * beat + beat * 2 / 3, 0.35);
                break;
            case TRIPLET:
                // 12/8: kick on 1 and 3, brushes on 2 and 4, hi-hat on every triplet.
                for (int b = 0; b < 4; b++) {
                    double t = t0 + b * beat;
                    if (b % 2 == 0) s.kick(t, 0.85); else s.brush(t, 0.85);
                    for (int k = 0; k < 3; k++) s.hat(t + k * beat / 3, k == 0 ? 0.5 : 0.3, false);
                }
                break;
            case SIXTEENTHS:
                // Funk: kick on 1, the "a" of 2 and the "and" of 3; snare on 2 and 4 with ghost notes; 16th hats.
                double q = beat / 4;
                s.kick(t0, 1.0); s.kick(t0 + 1 * beat + 3 * q, 0.75); s.kick(t0 + 2 * beat + 2 * q, 0.85);
                s.snare(t0 + beat, 0.85); s.snare(t0 + 3 * beat, 0.9);
                s.snare(t0 + 2 * beat + 3 * q, 0.2); s.snare(t0 + 3 * beat + 2 * q, 0.18);
                for (int k = 0; k < 16; k++) s.hat(t0 + k * q, k % 2 == 0 ? 0.5 : 0.25, k == 14);
                break;
        }
    }

    private static void bass(Synth s, Style style, Chord ch, Chord next, int bassMidi, double t0, double beat) {
        int root = bassMidi + ch.semitones;
        if (root > bassMidi + 7) root -= 12;
        switch (style.feel) {
            case SHUFFLE: {
                // Boogie line: root, 3rd, 5th, 6th (minor: root, ♭3, 5th, ♭7).
                int[] line = ch.quality == Quality.MIN7 ? new int[]{0, 3, 7, 10} : new int[]{0, 4, 7, 9};
                for (int b = 0; b < 4; b++) s.bass(t0 + b * beat, beat * 0.9, root + line[b], 0.9);
                break;
            }
            case TRIPLET:
                s.bass(t0, beat * 1.9, root, 0.95);
                s.bass(t0 + beat * 2 - beat / 3, beat / 3 * 0.9, root + 5, 0.6);
                s.bass(t0 + beat * 2, beat * 1.9, root + 7, 0.85);
                break;
            case SIXTEENTHS: {
                double q = beat / 4;
                s.bass(t0, q * 1.6, root, 1.0);
                s.bass(t0 + 3 * q, q * 0.8, root + 12, 0.7);
                s.bass(t0 + beat + 2 * q, q * 1.5, root, 0.85);
                s.bass(t0 + 2 * beat + 2 * q, q * 0.9, root + 10, 0.8);
                s.bass(t0 + 3 * beat + q, q * 0.9, root + 7, 0.75);
                s.bass(t0 + 3 * beat + 3 * q, q * 0.8, root + 5, 0.6);
                break;
            }
            case SWING: {
                // Walking: root, chord tones, then a half step into the next root.
                int nextRoot = bassMidi + next.semitones;
                if (nextRoot > bassMidi + 7) nextRoot -= 12;
                int third = root + ch.quality.tones[1][1], fifth = root + 7;
                int approach = nextRoot + (nextRoot > fifth ? -1 : 1);
                int[] line = {root, third, fifth, approach};
                for (int b = 0; b < 4; b++) s.bass(t0 + b * beat, beat * 0.95, line[b], 0.85);
                break;
            }
        }
    }

    private static void comp(Synth s, Style style, Chord ch, int bassMidi, double t0, double beat, int bar) {
        // Voicing around middle C: 3rd, 5th, 7th (and the 9th on dominant chords), kept near the same register.
        int base = bassMidi + 12 + ch.semitones;
        while (base < 52) base += 12;
        while (base > 62) base -= 12;
        int[] voicing = {base + ch.quality.tones[1][1], base + ch.quality.tones[2][1], base + ch.quality.tones[3][1], base + 14};
        switch (style.feel) {
            case SHUFFLE:
                for (int b : new int[]{1, 3}) s.chord(t0 + b * beat + beat * 2 / 3, beat * 0.3, voicing, 0.55);
                break;
            case SWING:
                // Charleston: beat 1 and the "and" of 2.
                s.chord(t0, beat * 0.6, voicing, 0.6);
                s.chord(t0 + beat + beat * 2 / 3, beat * 0.4, voicing, 0.5);
                break;
            case TRIPLET:
                s.chord(t0, beat * 3.8, voicing, 0.5);
                for (int b = 1; b < 4; b++) s.chord(t0 + b * beat + beat * 2 / 3, beat / 3, voicing, 0.3);
                break;
            case SIXTEENTHS: {
                double q = beat / 4;
                for (double off : new double[]{beat + 2 * q, 2 * beat + 3 * q, 3 * beat + 2 * q}) s.chord(t0 + off, q * 0.7, voicing, 0.5);
                break;
            }
        }
    }

    /** The instruments. Each event gets a few milliseconds of human timing and a little loudness variation. */
    static final class Synth {
        final float[] out;
        final int rate;
        final Random rnd;
        Synth(float[] out, int rate, Random rnd) { this.out = out; this.rate = rate; this.rnd = rnd; }

        private double human(double t) { return t + (rnd.nextGaussian() * 0.004); }
        private double vel(double v) { return v * (0.88 + 0.24 * rnd.nextDouble()); }
        private int start(double t) { return (int) Math.round(Math.max(0, human(t)) * rate); }

        /** Plucked string (Karplus–Strong) with a little sine body, for the bass. */
        void bass(double t, double dur, double midi, double v) {
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

        void chord(double t, double dur, int[] notes, double v) {
            double strum = 0;
            for (int m : notes) { piano(t + strum, dur, m, v); strum += 0.006; }
        }

        void kick(double t, double v) {
            int s0 = start(t), len = (int) (0.35 * rate);
            double a = 0.75 * vel(v), phase = 0;
            for (int i = 0; i < len && s0 + i < out.length; i++) {
                double x = (double) i / rate;
                phase += 2 * Math.PI * (48 + 110 * Math.exp(-x / 0.025)) / rate;
                double click = i < rate * 0.002 ? (rnd.nextDouble() * 2 - 1) * 0.3 : 0;
                out[s0 + i] += (float) (a * (Math.exp(-x / 0.11) * Math.sin(phase) + click));
            }
        }

        void snare(double t, double v) {
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
        void brush(double t, double v) {
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

        void hat(double t, double v, boolean open) { metal(t, v, open ? 0.12 : 0.025, 0.12, 8); }

        void ride(double t, double v) { metal(t, v, 0.45, 0.06, 5.5); }

        /** Reedy lead voice with vibrato and a little breath, for the call phrases. */
        void lead(double t, double dur, double f) {
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
