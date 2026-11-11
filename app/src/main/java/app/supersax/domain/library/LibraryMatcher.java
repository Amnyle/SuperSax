package app.supersax.domain.library;

import app.supersax.domain.common.PieceProgressRecord;
import app.supersax.domain.common.PieceStage;
import app.supersax.domain.pitch.Instrument;
import app.supersax.domain.planner.PieceChoice;
import app.supersax.domain.program.PlayerState;
import app.supersax.domain.program.ScaleProgress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Matches pieces to the player.
 *
 * <p>A piece unlocks when its written range fits the player's range and the scale of its key (or its
 * relative) has a clean pair. It is a <b>stretch</b> when exactly one requirement is missing by a small
 * margin: the range exceeded by at most {@link #STRETCH_SEMITONES}, or the key's scale being the next one
 * to unlock. A recorded stage (learning, playable, clean at tempo) always wins.
 */
public final class LibraryMatcher {
    public static final int STRETCH_SEMITONES = 2;

    private LibraryMatcher() {}

    private static @Nullable PieceProgressRecord wholeProgress(List<PieceProgressRecord> progress, String pieceId) {
        PieceProgressRecord best = null;
        for (PieceProgressRecord p : progress) {
            if (p.getPieceId().equals(pieceId) && p.getSectionId().isEmpty() && (best == null || p.getUpdatedAtMs() > best.getUpdatedAtMs())) best = p;
        }
        return best;
    }

    public static PieceMatch match(Piece piece, PlayerState player, List<PieceProgressRecord> progress, @Nullable String nextUnlock, Instrument instrument) {
        PieceProgressRecord rec = wholeProgress(progress, piece.getId());
        PieceStage stage = rec != null ? rec.getStage() : null;
        List<Missing> missing = missing(piece, player, nextUnlock, instrument);
        PieceState state;
        if (stage == PieceStage.CLEAN_AT_TEMPO) state = PieceState.CLEAN_AT_TEMPO;
        else if (stage == PieceStage.PLAYABLE) state = PieceState.PLAYABLE;
        else if (stage == PieceStage.LEARNING) state = PieceState.LEARNING;
        else if (missing.isEmpty()) state = PieceState.AVAILABLE;
        else if (missing.size() == 1 && isSmall(missing.get(0))) state = PieceState.STRETCH;
        else state = PieceState.LOCKED;
        return new PieceMatch(piece, state, missing);
    }

    public static PieceMatch match(Piece piece, PlayerState player, List<PieceProgressRecord> progress, @Nullable String nextUnlock) {
        return match(piece, player, progress, nextUnlock, Instrument.ALTO_SAX);
    }

    private static boolean isSmall(Missing m) {
        if (m instanceof Missing.RangeLow) return ((Missing.RangeLow) m).getSemitones() <= STRETCH_SEMITONES;
        if (m instanceof Missing.RangeHigh) return ((Missing.RangeHigh) m).getSemitones() <= STRETCH_SEMITONES;
        if (m instanceof Missing.Scale) return ((Missing.Scale) m).isNextUnlock();
        return false;
    }

    public static List<Missing> missing(Piece piece, PlayerState player, @Nullable String nextUnlock, Instrument instrument) {
        List<Missing> out = new ArrayList<>();
        if (!piece.getHasNotes()) { out.add(Missing.NoNotes.INSTANCE); return out; }
        int low = instrument.concertToWrittenMidi(piece.getLowMidi());
        int high = instrument.concertToWrittenMidi(piece.getHighMidi());
        if (low < player.getWrittenLowMidi()) out.add(new Missing.RangeLow(player.getWrittenLowMidi() - low, low));
        if (high > player.getWrittenHighMidi()) out.add(new Missing.RangeHigh(high - player.getWrittenHighMidi(), high));
        Set<String> clean = new HashSet<>();
        for (ScaleProgress s : player.getScales()) if (s.getBestCleanBpm() > 0) clean.add(s.getScaleKey());
        String key = piece.getScaleKey();
        String relative = piece.getRelativeScaleKey();
        if (key != null && !clean.contains(key) && !clean.contains(relative)) {
            out.add(new Missing.Scale(key, nextUnlock != null && (nextUnlock.equals(key) || nextUnlock.equals(relative))));
        }
        return out;
    }

    public static List<Missing> missing(Piece piece, PlayerState player, @Nullable String nextUnlock) {
        return missing(piece, player, nextUnlock, Instrument.ALTO_SAX);
    }

    private static long latest(List<PieceProgressRecord> progress, String pieceId, boolean played) {
        long t = 0;
        for (PieceProgressRecord p : progress) if (p.getPieceId().equals(pieceId)) t = Math.max(t, played ? p.getLastPlayedAtMs() : p.getUpdatedAtMs());
        return t;
    }

    private static final Comparator<PieceMatch> EASIEST = Comparator.<PieceMatch>comparingInt(m -> m.getPiece().getDifficulty()).thenComparing(m -> m.getPiece().getId());

    /**
     * Today's pieces: "something new" continues the piece being learned (most recently touched); if none,
     * the easiest Available piece not yet started, then the easiest Stretch. "Something fun" is the
     * Playable or Clean piece played least recently. The section is the first one not yet playable.
     */
    public static PieceChoice choose(List<PieceMatch> matches, List<PieceProgressRecord> progress) {
        Set<String> started = new HashSet<>();
        for (PieceProgressRecord p : progress) started.add(p.getPieceId());
        PieceMatch learning = null;
        for (PieceMatch m : matches) {
            if (m.getState() == PieceState.LEARNING && (learning == null
                || latest(progress, m.getPiece().getId(), false) > latest(progress, learning.getPiece().getId(), false))) learning = m;
        }
        PieceMatch chosen = learning;
        for (PieceState wanted : new PieceState[]{PieceState.AVAILABLE, PieceState.STRETCH}) {
            if (chosen != null) break;
            for (PieceMatch m : matches) {
                if (started.contains(m.getPiece().getId()) || m.getState() != wanted) continue;
                if (chosen == null || EASIEST.compare(m, chosen) < 0) chosen = m;
            }
        }
        String section = null;
        if (chosen != null) {
            for (PieceSection s : chosen.getPiece().getFile().getSections()) {
                PieceStage st = null;
                for (PieceProgressRecord p : progress) if (p.getPieceId().equals(chosen.getPiece().getId()) && p.getSectionId().equals(s.getId())) { st = p.getStage(); break; }
                if (st == null || st.compareTo(PieceStage.PLAYABLE) < 0) { section = s.getId(); break; }
            }
        }
        PieceMatch fun = null;
        for (PieceMatch m : matches) {
            if (m.getState() != PieceState.PLAYABLE && m.getState() != PieceState.CLEAN_AT_TEMPO) continue;
            if (fun == null) { fun = m; continue; }
            long a = latest(progress, m.getPiece().getId(), true), b = latest(progress, fun.getPiece().getId(), true);
            if (a < b || (a == b && m.getPiece().getId().compareTo(fun.getPiece().getId()) < 0)) fun = m;
        }
        return new PieceChoice(chosen != null ? chosen.getPiece().getId() : null, section, fun != null ? fun.getPiece().getId() : null);
    }
}
