package app.supersax.domain.pitch;

import java.util.Objects;

/** A pitch class with spelling: a letter and an alteration (-2..+2). */
public final class PitchClass {
    private final Letter letter;
    private final int alter;

    public PitchClass(Letter letter, int alter) {
        this.letter = letter;
        this.alter = alter;
    }

    public PitchClass(Letter letter) { this(letter, 0); }

    public Letter getLetter() { return letter; }

    public int getAlter() { return alter; }

    /** 0..11 from C. */
    public int getSemitone() { return Math.floorMod(letter.getSemitone() + alter, 12); }

    public PitchClass transpose(Interval interval) {
        return new SpelledPitch(letter, alter, 4).transpose(interval).getPitchClass();
    }

    /** ASCII spelling such as "Eb" or "F#". Used as a stable id in data files. */
    public String ascii() { return letter.name() + Accidentals.ascii(alter); }

    public static PitchClass parse(String s) {
        Letter l = Letter.valueOf(s.substring(0, 1).toUpperCase());
        return new PitchClass(l, Accidentals.parse(s.substring(1)));
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof PitchClass)) return false;
        PitchClass p = (PitchClass) o;
        return letter == p.letter && alter == p.alter;
    }

    @Override public int hashCode() { return Objects.hash(letter, alter); }

    @Override public String toString() { return ascii(); }
}
