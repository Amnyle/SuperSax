package app.supersax.domain.pitch;

/** Frequency and cents helpers. Reference pitch A4 defaults to 440 Hz. */
public final class Tuning {
    public static final double DEFAULT_A4 = 440.0;

    private Tuning() {}

    public static double midiToFrequency(double midi, double a4) { return a4 * Math.pow(2.0, (midi - 69.0) / 12.0); }

    public static double midiToFrequency(double midi) { return midiToFrequency(midi, DEFAULT_A4); }

    public static double midiToFrequency(int midi, double a4) { return midiToFrequency((double) midi, a4); }

    public static double midiToFrequency(int midi) { return midiToFrequency((double) midi, DEFAULT_A4); }

    /** Fractional MIDI number of a frequency. */
    public static double frequencyToMidi(double f, double a4) { return 69.0 + 12.0 * log2(f / a4); }

    public static double frequencyToMidi(double f) { return frequencyToMidi(f, DEFAULT_A4); }

    /** Cents between {@code f} and {@code fRef}: {@code 1200 * log2(f / fRef)}. */
    public static double cents(double f, double fRef) { return 1200.0 * log2(f / fRef); }

    private static double log2(double x) { return Math.log(x) / Math.log(2.0); }
}
