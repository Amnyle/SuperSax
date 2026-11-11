package app.supersax.domain.library;

import app.supersax.domain.pitch.Instrument;
import app.supersax.domain.pitch.KeySignature;
import app.supersax.domain.pitch.PitchClass;
import app.supersax.domain.scale.ScaleId;
import app.supersax.domain.scale.ScaleType;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** A loaded piece: metadata, parsed score (null for metadata-only entries) and computed metrics. */
public final class Piece {
    private final PieceFile file;
    private final @Nullable Score score;
    private final int difficulty;
    /** Lowest and highest concert MIDI notes, or null. */
    private final @Nullable Integer lowMidi, highMidi;
    private final int durationSec;

    public Piece(PieceFile file, @Nullable Score score, int difficulty, @Nullable Integer lowMidi, @Nullable Integer highMidi, int durationSec) {
        this.file = file; this.score = score; this.difficulty = difficulty;
        this.lowMidi = lowMidi; this.highMidi = highMidi; this.durationSec = durationSec;
    }

    public PieceFile getFile() { return file; }
    public @Nullable Score getScore() { return score; }
    public int getDifficulty() { return difficulty; }
    public @Nullable Integer getLowMidi() { return lowMidi; }
    public @Nullable Integer getHighMidi() { return highMidi; }
    public int getDurationSec() { return durationSec; }
    public String getId() { return file.getId(); }

    public boolean getHasNotes() {
        if (score == null) return false;
        for (ScoreNote n : score.getNotes()) if (!n.isRest()) return true;
        return false;
    }

    private @Nullable String keyOf(boolean relative) {
        Integer k = file.getKey();
        if (k == null) return null;
        KeySignature sig = new KeySignature(k);
        boolean minor = "minor".equals(file.getMode()) != relative;
        PitchClass tonic = minor ? sig.getMinorTonic() : sig.getMajorTonic();
        ScaleType type = minor ? ScaleType.NATURAL_MINOR : ScaleType.MAJOR;
        return new ScaleId(ScaleId.normaliseConcertTonic(tonic, type), type).getKey();
    }

    /** The scale of the piece's key (concert), from key signature and mode. */
    public @Nullable String getScaleKey() { return keyOf(false); }

    /** The relative scale (relative minor of a major key and vice versa). */
    public @Nullable String getRelativeScaleKey() { return keyOf(true); }

    /** Parses a piece file and computes its metrics. */
    public static Piece from(PieceFile file, Instrument instrument) {
        Score score = null;
        if (file.getBundled() && file.getKey() != null && file.getTime() != null && !file.getMeasures().isEmpty()) {
            String[] t = file.getTime().split("/");
            List<Measure> measures = new ArrayList<>();
            for (String m : file.getMeasures()) measures.add(Score.parseMeasure(m));
            score = new Score(file.getKey(), Integer.parseInt(t[0]), Integer.parseInt(t[1]), file.getTempo() != null ? file.getTempo() : 80,
                measures, file.getPickup());
        }
        Integer lo = null, hi = null;
        if (score != null) for (ScoreNote n : score.getNotes()) {
            if (n.getPitch() == null) continue;
            int m = n.getPitch().getMidi();
            lo = lo == null ? m : Math.min(lo, m);
            hi = hi == null ? m : Math.max(hi, m);
        }
        return new Piece(file, score, score != null ? Difficulty.score(score, instrument) : 0, lo, hi,
            score != null ? Difficulty.durationSec(score) : 0);
    }

    public static Piece from(PieceFile file) { return from(file, Instrument.ALTO_SAX); }
}
