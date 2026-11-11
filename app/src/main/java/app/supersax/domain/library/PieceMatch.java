package app.supersax.domain.library;

import java.util.List;

/** A piece with its state for this player and what is missing. */
public final class PieceMatch {
    private final Piece piece;
    private final PieceState state;
    private final List<Missing> missing;

    public PieceMatch(Piece piece, PieceState state, List<Missing> missing) { this.piece = piece; this.state = state; this.missing = missing; }

    public Piece getPiece() { return piece; }
    public PieceState getState() { return state; }
    public List<Missing> getMissing() { return missing; }
}
