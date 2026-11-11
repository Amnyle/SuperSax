package app.supersax.domain.library;

import app.supersax.domain.pitch.Instrument;
import app.supersax.domain.pitch.KeySignature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Difficulty from 1 to 10, computed from the score as the alto player reads it (written pitch).
 *
 * <p>Eight features, each with a maximum weight (sum 10.5), then scaled to 1..10:
 * <ul>
 *   <li>range span: 0 up to 7 semitones, full 1.5 at 24 or more;</li>
 *   <li>extremes: 0.75 if any note is under written D4 (low B♭–C♯), 0.75 if any is above written C6
 *       (palm keys and up), +0.5 for altissimo;</li>
 *   <li>key signature size: |written fifths| / 6 × 1.5;</li>
 *   <li>accidentals outside the key: share of notes × 5, capped at 1.0;</li>
 *   <li>shortest note: quarter or longer 0, eighth 0.6, sixteenth or shorter 1.2;</li>
 *   <li>rhythm: dotted notes, ties and syncopation (notes starting off the beat and crossing it), up to 1.3;</li>
 *   <li>speed: notes per second, 0 at 1/s, full 2.0 at 5/s;</li>
 *   <li>length: 0 up to 8 bars, full 0.5 at 48 bars.</li>
 * </ul>
 */
public final class Difficulty {
    private static final double MAX = 10.5;
    private static final double EPS = 1e-9;

    private Difficulty() {}

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    public static Map<String, Double> features(Score score, Instrument instrument) {
        Score written = score.toWritten(instrument);
        List<ScoreNote> all = written.getNotes();
        List<ScoreNote> notes = new ArrayList<>();
        for (ScoreNote n : all) if (!n.isRest()) notes.add(n);
        if (notes.isEmpty()) return Collections.emptyMap();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        boolean low = false, high = false, altissimo = false;
        KeySignature key = new KeySignature(written.getKeyFifths());
        int accidentals = 0;
        for (ScoreNote n : notes) {
            int m = n.getPitch().getMidi();
            min = Math.min(min, m); max = Math.max(max, m);
            if (m < 62) low = true;
            if (m > 84) high = true;
            if (m > instrument.getWrittenHighMidi()) altissimo = true;
            if (n.getPitch().getAlter() != key.alterFor(n.getPitch().getLetter())) accidentals++;
        }
        double fSpan = clamp((max - min - 7) / 17.0, 0.0, 1.0) * 1.5;
        double fExt = (low ? 0.75 : 0) + (high ? 0.75 : 0) + (altissimo ? 0.5 : 0);
        double fKey = Math.abs(written.getKeyFifths()) / 6.0 * 1.5;
        double fAcc = Math.min(1.0, (double) accidentals / notes.size() * 5);
        double shortest = Double.MAX_VALUE;
        for (ScoreNote n : all) shortest = Math.min(shortest, n.getDuration().getValue());
        double fShort = shortest >= 0.25 - EPS ? 0.0 : shortest >= 0.125 - EPS ? 0.6 : 1.2;
        // Rhythm: dotted notes, ties, syncopations (relative to the beat 1/den).
        double beat = 1.0 / written.getTimeDen();
        int syncopations = 0, dotted = 0, ties = 0;
        for (Measure m : written.getMeasures()) {
            double pos = 0.0;
            for (ScoreNote n : m.getNotes()) {
                double d = n.getDuration().getValue();
                boolean offBeat = (pos / beat) % 1.0 > 1e-6;
                boolean crosses = Math.floor((pos + d) / beat - EPS) > Math.floor(pos / beat + EPS);
                if (!n.isRest() && offBeat && crosses) syncopations++;
                if (n.getDuration().getNum() == 3) dotted++;
                if (n.getTie()) ties++;
                pos += d;
            }
        }
        double total = all.size();
        double fRhythm = Math.min(1.3, dotted / total * 2.0 + ties / total * 2.0 + syncopations / total * 4.0);
        int seconds = Math.max(1, durationSec(score));
        double nps = notes.size() / (double) seconds;
        double fSpeed = clamp((nps - 1.0) / 4.0, 0.0, 1.0) * 2.0;
        double fLen = clamp((written.getMeasures().size() - 8) / 40.0, 0.0, 1.0) * 0.5;
        Map<String, Double> f = new LinkedHashMap<>();
        f.put("span", fSpan); f.put("extremes", fExt); f.put("key", fKey); f.put("accidentals", fAcc);
        f.put("shortest", fShort); f.put("rhythm", fRhythm); f.put("speed", fSpeed); f.put("length", fLen);
        return f;
    }

    public static Map<String, Double> features(Score score) { return features(score, Instrument.ALTO_SAX); }

    public static int score(Score score, Instrument instrument) {
        Map<String, Double> f = features(score, instrument);
        if (f.isEmpty()) return 1;
        double sum = 0;
        for (double v : f.values()) sum += v;
        return (int) clamp(Math.round(1 + sum * 9 / MAX), 1, 10);
    }

    public static int score(Score score) { return score(score, Instrument.ALTO_SAX); }

    /** Playing time at the written tempo (beats of 1/den per minute). */
    public static int durationSec(Score score) {
        double wholes = 0;
        for (Measure m : score.getMeasures()) wholes += m.getLength().getValue();
        return (int) Math.round(wholes * score.getTimeDen() * 60.0 / score.getTempo());
    }
}
