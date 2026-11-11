package app.supersax.domain.pitch;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A spelled pitch: letter, alteration and octave (scientific pitch notation, C4 = middle C = MIDI 60).
 *
 * <p>Concert pitch is the storage standard everywhere in the app; written alto pitch is derived with
 * {@link Instrument#concertToWritten}.
 */
public final class SpelledPitch implements Comparable<SpelledPitch> {
    private static final Pattern PATTERN = Pattern.compile("^([A-Ga-g])(bb|b|##|#|x|n)?(-?\\d+)$");
    private static final Letter[] DEFAULT_LETTER = {
        Letter.C, Letter.C, Letter.D, Letter.E, Letter.E, Letter.F, Letter.F, Letter.G, Letter.G, Letter.A, Letter.B, Letter.B,
    };
    private static final int[] DEFAULT_ALTER = {0, 1, 0, -1, 0, 0, 1, 0, 1, 0, -1, 0};

    private final Letter letter;
    private final int alter;
    private final int octave;

    public SpelledPitch(Letter letter, int alter, int octave) {
        this.letter = letter;
        this.alter = alter;
        this.octave = octave;
    }

    public Letter getLetter() { return letter; }

    public int getAlter() { return alter; }

    public int getOctave() { return octave; }

    /** MIDI number, A4 = 69, C4 = 60. */
    public int getMidi() { return (octave + 1) * 12 + letter.getSemitone() + alter; }

    /** Absolute diatonic index (C0 = 0); one unit is one staff step (line to space). */
    public int getDiatonicIndex() { return octave * 7 + letter.getIndex(); }

    public PitchClass getPitchClass() { return new PitchClass(letter, alter); }

    /** Transposes with correct spelling: the letter moves by the diatonic steps, the alteration fixes the semitones. */
    public SpelledPitch transpose(Interval interval) {
        int newDiatonic = getDiatonicIndex() + interval.getDiatonicSteps();
        Letter newLetter = Letter.ofIndex(newDiatonic);
        int newOctave = Math.floorDiv(newDiatonic, 7);
        int naturalMidi = (newOctave + 1) * 12 + newLetter.getSemitone();
        return new SpelledPitch(newLetter, getMidi() + interval.getSemitones() - naturalMidi, newOctave);
    }

    /** Same sounding pitch, different spelling one letter away (G♯ ↔ A♭). */
    public SpelledPitch respell(int letterShift) { return transpose(new Interval(letterShift, 0)); }

    @Override public int compareTo(SpelledPitch other) {
        int c = Integer.compare(getMidi(), other.getMidi());
        return c != 0 ? c : Integer.compare(getDiatonicIndex(), other.getDiatonicIndex());
    }

    /** ASCII form such as "Eb4", "F#5", "Bbb3", "C##4". */
    @Override public String toString() { return letter.name() + Accidentals.ascii(alter) + octave; }

    @Override public boolean equals(Object o) {
        if (!(o instanceof SpelledPitch)) return false;
        SpelledPitch p = (SpelledPitch) o;
        return letter == p.letter && alter == p.alter && octave == p.octave;
    }

    @Override public int hashCode() { return Objects.hash(letter, alter, octave); }

    public static SpelledPitch parse(String s) {
        Matcher m = PATTERN.matcher(s.trim());
        if (!m.matches()) throw new IllegalArgumentException("Bad pitch: " + s);
        Letter l = Letter.valueOf(m.group(1).toUpperCase());
        String acc = m.group(2) == null ? "" : m.group(2);
        return new SpelledPitch(l, Accidentals.parse(acc), Integer.parseInt(m.group(3)));
    }

    /** Default chromatic spelling used for detected pitches: C C♯ D E♭ E F F♯ G G♯ A B♭ B. */
    public static SpelledPitch fromMidi(int midi) {
        int pc = Math.floorMod(midi, 12);
        return new SpelledPitch(DEFAULT_LETTER[pc], DEFAULT_ALTER[pc], Math.floorDiv(midi, 12) - 1);
    }
}
