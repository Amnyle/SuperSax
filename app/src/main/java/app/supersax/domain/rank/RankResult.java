package app.supersax.domain.rank;

import app.supersax.domain.program.Pillar;
import java.util.Map;

/** The computed rank. */
public final class RankResult {
    private final Map<Pillar, PillarResult> pillars;
    private final double average;
    private final int overallTier, division, musicianshipCap;
    private final Pillar limiter;

    public RankResult(Map<Pillar, PillarResult> pillars, double average, int overallTier, int division, Pillar limiter, int musicianshipCap) {
        this.pillars = pillars; this.average = average; this.overallTier = overallTier; this.division = division;
        this.limiter = limiter; this.musicianshipCap = musicianshipCap;
    }

    public Map<Pillar, PillarResult> getPillars() { return pillars; }
    public double getAverage() { return average; }
    /** 0 = Unranked, 1 = Iron .. 8 = Master. */
    public int getOverallTier() { return overallTier; }
    /** 0 = IV (lowest) .. 3 = I (highest); -1 for Master and Unranked. */
    public int getDivision() { return division; }
    public Pillar getLimiter() { return limiter; }
    /** Cap applied to Musicianship: floor(average of the other five) + 1. */
    public int getMusicianshipCap() { return musicianshipCap; }
}
