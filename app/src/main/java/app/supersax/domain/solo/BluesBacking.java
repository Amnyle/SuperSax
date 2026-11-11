package app.supersax.domain.solo;

import java.util.List;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Renders one chorus (12 bars, 4/4) of a shuffle blues backing, mono: kick on 1 and 3, snare on 2 and 4,
 * swung hi-hat, a boogie bass line (root 3 5 6 per bar), short chord stabs on the off-beats, and
 * optionally a lead voice playing call phrases in bars 1–2, 5–6 and 9–10 (the player answers in the
 * next two bars). All synthesised; the chorus loops seamlessly.
 */
public final class BluesBacking {
    private BluesBacking() {}

    /** Seconds of one chorus. */
    public static double chorusSeconds(double bpm) { return 12 * 4 * 60.0 / bpm; }

    /**
     * @param keyMidi concert MIDI of the key's root for the bass (e.g. 46 = B♭2)
     * @param calls three 2-bar phrases for bars 1, 5 and 9 (null: no lead voice)
     * @param leadMidi concert MIDI of the lead voice's root (e.g. 58 = B♭3)
     */
    public static float[] render(int keyMidi, double bpm, int rate, @Nullable List<List<Blues.PhraseNote>> calls, int leadMidi) {
        double beat = 60.0 / bpm;
        int n = (int) Math.round(chorusSeconds(bpm) * rate);
        float[] out = new float[n];
        Random rnd = new Random(7);
        double swing = 2.0 / 3.0; // the off-beat eighth falls two thirds into the beat
        for (int bar = 0; bar < 12; bar++) {
            int degree = Blues.FORM.get(bar);
            int root = keyMidi + (degree == 0 ? 0 : degree == 3 ? 5 : 7);
            for (int b = 0; b < 4; b++) {
                double t = (bar * 4 + b) * beat;
                // Drums.
                if (b == 0 || b == 2) kick(out, rate, t);
                else snare(out, rate, t, rnd);
                hat(out, rate, t, rnd, 0.10f);
                hat(out, rate, t + swing * beat, rnd, 0.06f);
                // Boogie bass: root, 3, 5, 6.
                int[] walk = {0, 4, 7, 9};
                tone(out, rate, t, beat * 0.9, midiHz(root + walk[b]), 0.32f, 2, 0.004, 0.25);
                // Chord stabs (3 and ♭7 with the 5) on the swung off-beat of beats 2 and 4.
                if (b == 1 || b == 3) {
                    double s = t + swing * beat;
                    for (int iv : new int[]{4 + 12, 7 + 12, 10 + 12}) tone(out, rate, s, beat * 0.28, midiHz(root + iv), 0.07f, 3, 0.003, 0.12);
                }
            }
        }
        if (calls != null) {
            int[] startBars = {0, 4, 8};
            for (int i = 0; i < Math.min(3, calls.size()); i++) {
                for (Blues.PhraseNote p : calls.get(i)) {
                    double whole = startBars[i] * 4 + p.getStartBeat();
                    double start = (Math.floor(whole) + (whole % 1 >= 0.5 ? swing : 0)) * beat;
                    lead(out, rate, start, p.getBeats() * beat * 0.92, midiHz(leadMidi + p.getSemitones()));
                }
            }
        }
        // Gentle limiter.
        float peak = 0;
        for (float v : out) peak = Math.max(peak, Math.abs(v));
        float g = peak > 0.9f ? 0.9f / peak : 1f;
        for (int i = 0; i < n; i++) out[i] = (float) Math.tanh(out[i] * g * 1.1) * 0.9f;
        return out;
    }

    static double midiHz(int midi) { return 440.0 * Math.pow(2, (midi - 69) / 12.0); }

    /** A plucked or struck tone with [harmonics] partials, a short attack and an exponential decay. */
    private static void tone(float[] out, int rate, double t, double dur, double hz, float amp, int harmonics, double attack, double decay) {
        int s0 = (int) (t * rate), len = (int) (dur * rate);
        for (int i = 0; i < len && s0 + i < out.length; i++) {
            if (s0 + i < 0) continue;
            double x = (double) i / rate;
            double env = Math.min(1, x / attack) * Math.exp(-x / decay) * Math.min(1, (len - i) / (0.01 * rate));
            double v = 0;
            for (int h = 1; h <= harmonics; h++) v += Math.sin(2 * Math.PI * hz * h * x) / h;
            out[s0 + i] += (float) (amp * env * v);
        }
    }

    /** A soft reedy lead voice with a little vibrato, for the call phrases. */
    private static void lead(float[] out, int rate, double t, double dur, double hz) {
        int s0 = (int) (t * rate), len = (int) (dur * rate);
        double phase = 0;
        for (int i = 0; i < len && s0 + i < out.length; i++) {
            double x = (double) i / rate;
            double env = Math.min(1, x / 0.03) * Math.min(1, (len - i) / (0.04 * rate)) * (0.85 + 0.15 * Math.exp(-x / 0.2));
            double f = hz * (1 + 0.004 * Math.sin(2 * Math.PI * 5.5 * x) * Math.min(1, x / 0.25));
            phase += 2 * Math.PI * f / rate;
            double v = Math.sin(phase) + 0.45 * Math.sin(2 * phase) + 0.3 * Math.sin(3 * phase) + 0.15 * Math.sin(4 * phase);
            out[s0 + i] += (float) (0.16 * env * v);
        }
    }

    private static void kick(float[] out, int rate, double t) {
        int s0 = (int) (t * rate), len = (int) (0.18 * rate);
        double phase = 0;
        for (int i = 0; i < len && s0 + i < out.length; i++) {
            double x = (double) i / rate;
            double f = 50 + 70 * Math.exp(-x / 0.03);
            phase += 2 * Math.PI * f / rate;
            out[s0 + i] += (float) (0.55 * Math.exp(-x / 0.06) * Math.sin(phase));
        }
    }

    private static void snare(float[] out, int rate, double t, Random rnd) {
        int s0 = (int) (t * rate), len = (int) (0.16 * rate);
        double lp = 0;
        for (int i = 0; i < len && s0 + i < out.length; i++) {
            double x = (double) i / rate;
            double noise = rnd.nextDouble() * 2 - 1;
            lp += 0.5 * (noise - lp);
            out[s0 + i] += (float) (0.22 * Math.exp(-x / 0.05) * (lp + 0.4 * Math.sin(2 * Math.PI * 190 * x)));
        }
    }

    private static void hat(float[] out, int rate, double t, Random rnd, float amp) {
        int s0 = (int) (t * rate), len = (int) (0.04 * rate);
        double prev = 0;
        for (int i = 0; i < len && s0 + i < out.length; i++) {
            double x = (double) i / rate;
            double noise = rnd.nextDouble() * 2 - 1;
            double hp = noise - prev; // crude high-pass
            prev = noise;
            out[s0 + i] += (float) (amp * Math.exp(-x / 0.012) * hp);
        }
    }
}
