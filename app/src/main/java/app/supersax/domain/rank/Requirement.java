package app.supersax.domain.rank;

import org.jetbrains.annotations.Nullable;

/**
 * One requirement of a tier, with where the player stands. {@code target} and {@code current} are in the
 * requirement's unit (count, BPM, MIDI, percent...). Extra parameters let the UI explain it: for
 * SCALES_AT_BPM a = minimum BPM; for HOLD_NOTES a = seconds, b = tolerance cents, c = in-tune share; for
 * READING_ROUND a = accuracy, b = mean ms; for SCALE_ROUND a = accuracy; for LESSONS a = minimum score.
 */
public final class Requirement {
    private final ReqKind kind;
    private final double target, current;
    private final boolean met;
    private final @Nullable ScaleFamily family;
    private final @Nullable Double a, b, c;

    public Requirement(ReqKind kind, double target, double current, boolean met, @Nullable ScaleFamily family,
                       @Nullable Double a, @Nullable Double b, @Nullable Double c) {
        this.kind = kind; this.target = target; this.current = current; this.met = met;
        this.family = family; this.a = a; this.b = b; this.c = c;
    }

    public Requirement(ReqKind kind, double target, double current, boolean met) { this(kind, target, current, met, null, null, null, null); }

    public ReqKind getKind() { return kind; }
    public double getTarget() { return target; }
    public double getCurrent() { return current; }
    public boolean getMet() { return met; }
    public @Nullable ScaleFamily getFamily() { return family; }
    public @Nullable Double getA() { return a; }
    public @Nullable Double getB() { return b; }
    public @Nullable Double getC() { return c; }
}
