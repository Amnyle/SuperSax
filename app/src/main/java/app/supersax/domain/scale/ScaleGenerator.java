package app.supersax.domain.scale;

import app.supersax.domain.pitch.Instrument;
import app.supersax.domain.pitch.Interval;
import app.supersax.domain.pitch.SpelledPitch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Generates spelled note sequences for scales and arpeggios. */
public final class ScaleGenerator {
    private ScaleGenerator() {}

    /** One octave, ascending, tonic to tonic, starting at {@code tonicOctave}. */
    public static List<SpelledPitch> oneOctave(ScaleId scale, int tonicOctave) {
        SpelledPitch root = new SpelledPitch(scale.getTonic().getLetter(), scale.getTonic().getAlter(), tonicOctave);
        List<SpelledPitch> out = new ArrayList<>();
        ScaleType t = scale.getType();
        for (int i = 0; i < t.getSteps().size(); i++) out.add(root.transpose(new Interval(t.getSteps().get(i), t.getSemitones().get(i))));
        out.add(root.transpose(Interval.OCTAVE));
        return out;
    }

    public static List<SpelledPitch> oneOctave(ScaleId scale) { return oneOctave(scale, 4); }

    /**
     * All notes of the scale between {@code lowMidi} and {@code highMidi} (concert, inclusive), ascending.
     * Used for "full range" scale runs on the player's instrument range.
     */
    public static List<SpelledPitch> inRange(ScaleId scale, int lowMidi, int highMidi) {
        List<SpelledPitch> all = new ArrayList<>();
        ScaleType t = scale.getType();
        for (int octave = -1; octave <= 9; octave++) {
            SpelledPitch root = new SpelledPitch(scale.getTonic().getLetter(), scale.getTonic().getAlter(), octave);
            for (int i = 0; i < t.getSteps().size(); i++) {
                SpelledPitch p = root.transpose(new Interval(t.getSteps().get(i), t.getSemitones().get(i)));
                if (p.getMidi() >= lowMidi && p.getMidi() <= highMidi) all.add(p);
            }
        }
        all.sort(Comparator.comparingInt(SpelledPitch::getMidi));
        List<SpelledPitch> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (SpelledPitch p : all) if (seen.add(p.getMidi())) out.add(p);
        return out;
    }

    /** Full range up and down (the top note played once), as counted by the Technique pillar. */
    public static List<SpelledPitch> fullRangeUpDown(ScaleId scale, int lowMidi, int highMidi) {
        List<SpelledPitch> up = inRange(scale, lowMidi, highMidi);
        List<SpelledPitch> out = new ArrayList<>(up);
        for (int i = up.size() - 2; i >= 0; i--) out.add(up.get(i));
        return out;
    }

    /** Full range on the instrument, in concert pitch. */
    public static List<SpelledPitch> fullRangeOnInstrument(ScaleId scale, Instrument instrument) {
        return fullRangeUpDown(scale, instrument.getConcertLowMidi(), instrument.getConcertHighMidi());
    }

    public static List<SpelledPitch> fullRangeOnInstrument(ScaleId scale) { return fullRangeOnInstrument(scale, Instrument.ALTO_SAX); }

    /** Builds a scale from a root and a W/H pattern, as in the "build the scale" drill. */
    public static List<SpelledPitch> fromPattern(SpelledPitch root, String pattern) {
        List<SpelledPitch> out = new ArrayList<>();
        out.add(root);
        SpelledPitch current = root;
        for (char c : pattern.toCharArray()) {
            current = current.transpose(new Interval(1, c == 'W' ? 2 : 1));
            out.add(current);
        }
        return out;
    }
}
