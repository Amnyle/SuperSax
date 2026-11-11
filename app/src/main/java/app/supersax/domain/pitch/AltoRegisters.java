package app.supersax.domain.pitch;

/** Alto register boundaries in written MIDI, used by onboarding, rank (Range, Sound) and drills. */
public final class AltoRegisters {
    public static final int LOW_BB = 58;
    public static final int LOW_D = 62;
    /** Written D5, first octave-key note. */
    public static final int MIDDLE_D = 74;
    /** Written D6. */
    public static final int PALM_D = 86;
    public static final int HIGH_FSHARP = 90;
    public static final int ALTISSIMO_G = 91;

    public enum Register { LOW, MIDDLE, HIGH, ALTISSIMO }

    private AltoRegisters() {}

    /** Register of a written MIDI note: low B♭3..C♯5, middle D5..C♯6, high D6..F♯6, altissimo above. */
    public static Register registerOf(int writtenMidi) {
        if (writtenMidi < MIDDLE_D) return Register.LOW;
        if (writtenMidi < PALM_D) return Register.MIDDLE;
        if (writtenMidi <= HIGH_FSHARP) return Register.HIGH;
        return Register.ALTISSIMO;
    }
}
