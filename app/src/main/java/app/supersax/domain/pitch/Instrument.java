package app.supersax.domain.pitch;

/**
 * Transposing instrument. Only the alto saxophone exists for now; the value is stored as a setting so a
 * tenor could be added later without touching stored data (everything is stored in concert pitch).
 */
public enum Instrument {
    /** E♭ alto: sounds a major sixth below written. Written B♭3..F♯6. */
    ALTO_SAX(Interval.MAJOR_SIXTH, 58, 90);

    private final Interval writtenAboveConcert;
    private final int writtenLowMidi;
    private final int writtenHighMidi;

    Instrument(Interval writtenAboveConcert, int writtenLowMidi, int writtenHighMidi) {
        this.writtenAboveConcert = writtenAboveConcert;
        this.writtenLowMidi = writtenLowMidi;
        this.writtenHighMidi = writtenHighMidi;
    }

    /** Interval from concert (sounding) pitch up to written pitch. */
    public Interval getWrittenAboveConcert() { return writtenAboveConcert; }

    /** Lowest written note of the standard range. */
    public int getWrittenLowMidi() { return writtenLowMidi; }

    /** Highest written note of the standard range (altissimo starts above). */
    public int getWrittenHighMidi() { return writtenHighMidi; }

    public int getConcertLowMidi() { return writtenLowMidi - writtenAboveConcert.getSemitones(); }

    public int getConcertHighMidi() { return writtenHighMidi - writtenAboveConcert.getSemitones(); }

    /** Key-signature shift from concert to written (alto: +3 sharps). */
    public int getFifthsShift() { return fifthsOfInterval(writtenAboveConcert); }

    /**
     * Interval to use for a piece or key in concert key {@code concertFifths}: the nominal interval, or its
     * enharmonic twin when the written key would need more than six sharps (concert E major is written
     * D♭ major rather than C♯ major).
     */
    public Interval writtenInterval(int concertFifths) {
        int w = concertFifths + getFifthsShift();
        if (w > 6) return new Interval(writtenAboveConcert.getDiatonicSteps() + 1, writtenAboveConcert.getSemitones());
        if (w < -6) return new Interval(writtenAboveConcert.getDiatonicSteps() - 1, writtenAboveConcert.getSemitones());
        return writtenAboveConcert;
    }

    public SpelledPitch concertToWritten(SpelledPitch p, int concertFifths) { return p.transpose(writtenInterval(concertFifths)); }

    public SpelledPitch concertToWritten(SpelledPitch p) { return concertToWritten(p, 0); }

    public SpelledPitch writtenToConcert(SpelledPitch p) { return p.transpose(writtenAboveConcert.unaryMinus()); }

    public int concertToWrittenMidi(int concertMidi) { return concertMidi + writtenAboveConcert.getSemitones(); }

    public int writtenToConcertMidi(int writtenMidi) { return writtenMidi - writtenAboveConcert.getSemitones(); }

    public KeySignature writtenKey(KeySignature concert) {
        return new KeySignature(concert.getFifths() + fifthsOfInterval(writtenInterval(concert.getFifths())));
    }

    /** Number of fifths an interval moves a key: M6 = +3, dim7 = -9. */
    public static int fifthsOfInterval(Interval i) {
        return KeySignature.majorFifths(new PitchClass(Letter.C).transpose(i));
    }
}
