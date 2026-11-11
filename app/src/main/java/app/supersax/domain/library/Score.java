package app.supersax.domain.library;

import app.supersax.domain.pitch.Instrument;
import app.supersax.domain.pitch.Interval;
import app.supersax.domain.pitch.KeySignature;
import app.supersax.domain.pitch.SpelledPitch;
import java.util.ArrayList;
import java.util.List;

/**
 * A single-voice score in one key and time signature. Pitches are concert (storage standard) unless the
 * score was produced by {@link #toWritten}.
 */
public final class Score {
    private final int keyFifths, timeNum, timeDen, tempo;
    private final List<Measure> measures;
    /** The first measure is an incomplete pickup (anacrusis). */
    private final boolean pickup;

    public Score(int keyFifths, int timeNum, int timeDen, int tempo, List<Measure> measures, boolean pickup) {
        this.keyFifths = keyFifths; this.timeNum = timeNum; this.timeDen = timeDen; this.tempo = tempo;
        this.measures = measures; this.pickup = pickup;
    }

    public Score(int keyFifths, int timeNum, int timeDen, int tempo, List<Measure> measures) { this(keyFifths, timeNum, timeDen, tempo, measures, false); }

    public int getKeyFifths() { return keyFifths; }
    public int getTimeNum() { return timeNum; }
    public int getTimeDen() { return timeDen; }
    public int getTempo() { return tempo; }
    public List<Measure> getMeasures() { return measures; }
    public boolean getPickup() { return pickup; }

    public Fraction getBarLength() { return new Fraction(timeNum, timeDen); }

    public KeySignature getKeySignature() { return new KeySignature(keyFifths); }

    /** Every note and rest in order. */
    public List<ScoreNote> getNotes() {
        List<ScoreNote> out = new ArrayList<>();
        for (Measure m : measures) out.addAll(m.getNotes());
        return out;
    }

    /** The same score with other measures (e.g. one section). */
    public Score withMeasures(List<Measure> m, boolean pickup) { return new Score(keyFifths, timeNum, timeDen, tempo, m, pickup); }

    /** Same music in written pitch for the instrument (key and notes transposed, sharp keys respelled). */
    public Score toWritten(Instrument instrument) {
        Interval interval = instrument.writtenInterval(keyFifths);
        List<Measure> out = new ArrayList<>();
        for (Measure m : measures) {
            List<ScoreNote> notes = new ArrayList<>();
            for (ScoreNote n : m.getNotes()) notes.add(n.withPitch(n.getPitch() == null ? null : n.getPitch().transpose(interval)));
            out.add(new Measure(notes));
        }
        return new Score(instrument.writtenKey(new KeySignature(keyFifths)).getFifths(), timeNum, timeDen, tempo, out, pickup);
    }

    public Score toWritten() { return toWritten(Instrument.ALTO_SAX); }

    /**
     * Parses the compact measure format used in piece files: space-separated tokens {@code PITCH:DURATION},
     * e.g. {@code Eb4:1/4}, {@code r:1/2} (rest), {@code C5:3/8~} (dotted quarter tied to the next note).
     */
    public static Measure parseMeasure(String text) {
        List<ScoreNote> notes = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            boolean tie = token.endsWith("~");
            String t = tie ? token.substring(0, token.length() - 1) : token;
            int colon = t.indexOf(':');
            String p = t.substring(0, colon);
            notes.add(new ScoreNote(p.equals("r") ? null : SpelledPitch.parse(p), Fraction.parse(t.substring(colon + 1)), tie));
        }
        return new Measure(notes);
    }

    public static String formatMeasure(Measure m) {
        StringBuilder b = new StringBuilder();
        for (ScoreNote n : m.getNotes()) {
            if (b.length() > 0) b.append(' ');
            b.append(n.getPitch() == null ? "r" : n.getPitch().toString()).append(':').append(n.getDuration());
            if (n.getTie()) b.append('~');
        }
        return b.toString();
    }
}
