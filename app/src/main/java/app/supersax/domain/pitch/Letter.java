package app.supersax.domain.pitch;

/** The seven note letters with their diatonic index and natural semitone offset from C. */
public enum Letter {
    C(0, 0), D(1, 2), E(2, 4), F(3, 5), G(4, 7), A(5, 9), B(6, 11);

    private final int index;
    private final int semitone;

    Letter(int index, int semitone) {
        this.index = index;
        this.semitone = semitone;
    }

    public int getIndex() { return index; }

    public int getSemitone() { return semitone; }

    /** Letter for any diatonic index (wraps every seven). */
    public static Letter ofIndex(int i) { return values()[Math.floorMod(i, 7)]; }
}
