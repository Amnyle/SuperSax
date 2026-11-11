package app.supersax.domain.audio;

import app.supersax.domain.pitch.Tuning;

/** Parameters of hold detection and analysis (immutable; use the {@code with…} methods to change one). */
public final class HoldConfig {
    /** Minimum YIN confidence for a frame to count as sound. */
    private final double minConfidence;
    /** A hold starts after this much continuous sound... */
    private final double onsetSec;
    /** ...and ends after this much continuous silence. */
    private final double releaseGapSec;
    /** Ignored at the start of a hold (attack: trackers are unreliable there). */
    private final double attackTrimSec;
    /** Ignored at the end of a hold (release). */
    private final double releaseTrimSec;
    private final double toleranceCents;
    /** Sampling rate of the stored trace. */
    private final double traceHz;
    private final double a4;

    public HoldConfig(double minConfidence, double onsetSec, double releaseGapSec, double attackTrimSec, double releaseTrimSec,
                      double toleranceCents, double traceHz, double a4) {
        this.minConfidence = minConfidence;
        this.onsetSec = onsetSec;
        this.releaseGapSec = releaseGapSec;
        this.attackTrimSec = attackTrimSec;
        this.releaseTrimSec = releaseTrimSec;
        this.toleranceCents = toleranceCents;
        this.traceHz = traceHz;
        this.a4 = a4;
    }

    /** Defaults: confidence 0.8, onset 150 ms, release gap 200 ms, trims 300/200 ms, ±10¢, 10 Hz trace, A4 = 440. */
    public HoldConfig() { this(0.80, 0.150, 0.200, 0.300, 0.200, 10.0, 10.0, Tuning.DEFAULT_A4); }

    public double getMinConfidence() { return minConfidence; }
    public double getOnsetSec() { return onsetSec; }
    public double getReleaseGapSec() { return releaseGapSec; }
    public double getAttackTrimSec() { return attackTrimSec; }
    public double getReleaseTrimSec() { return releaseTrimSec; }
    public double getToleranceCents() { return toleranceCents; }
    public double getTraceHz() { return traceHz; }
    public double getA4() { return a4; }

    public HoldConfig withToleranceCents(double v) {
        return new HoldConfig(minConfidence, onsetSec, releaseGapSec, attackTrimSec, releaseTrimSec, v, traceHz, a4);
    }

    public HoldConfig withA4(double v) {
        return new HoldConfig(minConfidence, onsetSec, releaseGapSec, attackTrimSec, releaseTrimSec, toleranceCents, traceHz, v);
    }
}
