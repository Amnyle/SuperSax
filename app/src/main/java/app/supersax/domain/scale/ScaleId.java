package app.supersax.domain.scale;

import app.supersax.domain.pitch.Instrument;
import app.supersax.domain.pitch.Interval;
import app.supersax.domain.pitch.KeySignature;
import app.supersax.domain.pitch.PitchClass;
import app.supersax.domain.pitch.SpelledPitch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A scale identity: a concert-pitch tonic and a type. Stored in concert pitch (the storage standard);
 * the UI shows the written name via {@link #writtenTonic}.
 */
public final class ScaleId {
    private final PitchClass tonic;
    private final ScaleType type;

    public ScaleId(PitchClass tonic, ScaleType type) {
        this.tonic = tonic;
        this.type = type;
    }

    public PitchClass getTonic() { return tonic; }

    public ScaleType getType() { return type; }

    /** Stable string id, e.g. "MAJOR:Eb" (concert). */
    public String getKey() { return type.name() + ":" + tonic.ascii(); }

    /** Concert key signature of this scale. */
    public KeySignature getKeySignature() { return new KeySignature(KeySignature.majorFifths(tonic) + type.getFifthsOffset()); }

    /** Tonic as the alto player reads it. */
    public PitchClass writtenTonic(Instrument instrument) { return tonic.transpose(instrument.writtenInterval(getKeySignature().getFifths())); }

    public PitchClass writtenTonic() { return writtenTonic(Instrument.ALTO_SAX); }

    public KeySignature writtenKeySignature(Instrument instrument) { return instrument.writtenKey(getKeySignature()); }

    public KeySignature writtenKeySignature() { return writtenKeySignature(Instrument.ALTO_SAX); }

    public static ScaleId parse(String key) {
        String[] parts = key.split(":");
        return new ScaleId(PitchClass.parse(parts[1]), ScaleType.valueOf(parts[0]));
    }

    /** Builds a scale id from the written tonic the player sees (written C major → concert E♭ major). */
    public static ScaleId fromWritten(PitchClass writtenTonic, ScaleType type, Instrument instrument) {
        PitchClass candidate = writtenTonic.transpose(instrument.getWrittenAboveConcert().unaryMinus());
        return new ScaleId(normaliseConcertTonic(candidate, type), type);
    }

    public static ScaleId fromWritten(PitchClass writtenTonic, ScaleType type) { return fromWritten(writtenTonic, type, Instrument.ALTO_SAX); }

    /** Keeps concert keys between 6 flats and 5 sharps (G♭ rather than F♯), the set used by {@link #all}. */
    public static PitchClass normaliseConcertTonic(PitchClass tonic, ScaleType type) {
        int f = KeySignature.majorFifths(tonic) + type.getFifthsOffset();
        if (f > 5) return tonic.transpose(new Interval(1, 0));
        if (f < -6) return tonic.transpose(new Interval(-1, 0));
        return tonic;
    }

    /** The 12 tonics of a scale type, concert spelling, key signatures from 6 flats to 5 sharps. */
    public static List<ScaleId> all(ScaleType type) {
        List<ScaleId> out = new ArrayList<>();
        for (int f = -6; f <= 5; f++) {
            ScaleId id = new ScaleId(modeTonic(new KeySignature(f), type), type);
            if (id.getKeySignature().getFifths() != f) throw new IllegalStateException("bad mode tonic for " + f);
            out.add(id);
        }
        return out;
    }

    /** Tonic of the scale of {@code type} whose key signature is {@code sig}. */
    static PitchClass modeTonic(KeySignature sig, ScaleType type) {
        PitchClass major = sig.getMajorTonic();
        int degree;
        switch (type) {
            case DORIAN: degree = 1; break;
            case PHRYGIAN: degree = 2; break;
            case LYDIAN: degree = 3; break;
            case MIXOLYDIAN: degree = 4; break;
            case NATURAL_MINOR: case MINOR_ARPEGGIO: case MINOR_PENTATONIC: degree = 5; break;
            case LOCRIAN: degree = 6; break;
            default: degree = 0;
        }
        SpelledPitch p = new SpelledPitch(major.getLetter(), major.getAlter(), 4);
        return p.transpose(new Interval(degree, ScaleType.MAJOR.getSemitones().get(degree))).getPitchClass();
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof ScaleId)) return false;
        ScaleId s = (ScaleId) o;
        return tonic.equals(s.tonic) && type == s.type;
    }

    @Override public int hashCode() { return Objects.hash(tonic, type); }

    @Override public String toString() { return getKey(); }
}
