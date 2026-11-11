package app.supersax.domain.rank;

import app.supersax.domain.program.Pillar;
import java.util.List;

/** One pillar: the tier counted in the overall rank, the tier before the Musicianship cap, and the next tier's requirements. */
public final class PillarResult {
    private final Pillar pillar;
    private final int tier, rawTier;
    private final List<Requirement> next;

    public PillarResult(Pillar pillar, int tier, int rawTier, List<Requirement> next) {
        this.pillar = pillar; this.tier = tier; this.rawTier = rawTier; this.next = next;
    }

    public Pillar getPillar() { return pillar; }
    /** Tier counted in the overall rank (Musicianship after the cap). */
    public int getTier() { return tier; }
    /** Tier before the Musicianship cap (same as the tier for the others). */
    public int getRawTier() { return rawTier; }
    /** Requirements of the next tier with progress; empty at Master. */
    public List<Requirement> getNext() { return next; }
}
