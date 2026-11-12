package app.supersax.domain.audio;

import java.util.List;

/** Short sounds rendered with a recorded instrument: a chord, or a melody of quarter-note beats. */
public final class Clips {
    private Clips() {}

    private static double shift(double a4) { return 12 * Math.log(a4 / 440.0) / Math.log(2); }

    /** All [concertMidi] notes together for [seconds] (plus the release). */
    public static float[] chord(SampledInstrument inst, int rate, List<Integer> concertMidi, double seconds, double a4) {
        float[] out = new float[(int) ((seconds + 0.6) * rate)];
        double g = 0.5 / Math.sqrt(Math.max(1, concertMidi.size()));
        int strum = 0;
        for (int m : concertMidi) { inst.play(out, rate, strum, m + shift(a4), g, seconds, 0.4); strum += rate / 200; }
        return out;
    }

    /** The notes one after the other at [bpm], each lasting its beats. */
    public static float[] melody(SampledInstrument inst, int rate, List<MelodyNote> notes, int bpm, double a4) {
        double beat = 60.0 / bpm, total = 0;
        for (MelodyNote n : notes) total += n.getBeats() * beat;
        float[] out = new float[(int) ((total + 0.8) * rate)];
        double t = 0;
        for (MelodyNote n : notes) {
            double d = n.getBeats() * beat;
            if (n.getMidi() != null) inst.play(out, rate, (int) (t * rate), n.getMidi() + shift(a4), 0.5, d * 0.95, 0.25);
            t += d;
        }
        return out;
    }

    /** A metronome click: [accent] higher and louder. */
    static void click(float[] out, int rate, int at, boolean accent, double gain) {
        double f = accent ? 2000 : 1400;
        int n = (int) (0.03 * rate);
        for (int i = 0; i < n && at + i < out.length; i++) {
            if (at + i < 0) continue;
            double x = (double) i / rate;
            out[at + i] += (float) (gain * Math.exp(-x / 0.008) * Math.sin(2 * Math.PI * f * x));
        }
    }

    /**
     * A rhythm bar after a count-in: [countIn] accented clicks, then the bar's clicks (softer), and, when [inst] is
     * given, the notes ([spans] in beats from the bar start) on [concertMidi].
     */
    public static float[] rhythm(@org.jetbrains.annotations.Nullable SampledInstrument inst, int rate, int bpm, List<double[]> spans,
                                 int countIn, int concertMidi, double a4) {
        double beat = 60.0 / bpm;
        float[] out = new float[(int) ((countIn + 4 + 1) * beat * rate)];
        for (int b = 0; b < countIn + 4; b++) click(out, rate, (int) (b * beat * rate), b % 4 == 0, b < countIn ? 0.55 : 0.3);
        if (inst != null) for (double[] s : spans) {
            inst.play(out, rate, (int) ((countIn + s[0]) * beat * rate), concertMidi + shift(a4), 0.5, (s[1] - s[0]) * beat * 0.97, 0.06);
        }
        return out;
    }
}
