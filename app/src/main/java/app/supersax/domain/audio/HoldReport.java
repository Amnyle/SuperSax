package app.supersax.domain.audio;

import java.util.List;

/**
 * Result of one long-tone hold. Cents are relative to {@code refMidi} (concert). The trace is the cents
 * offset downsampled to {@code traceHz}, so thresholds can be recomputed later ({@link #inTuneShareAt}).
 */
public final class HoldReport {
    private final double startSec, endSec;
    private final int refMidi;
    private final double stableSec, inTuneShare, toleranceCents, meanCents, driftCents, slopeCentsPerSec, minCents, maxCents;
    private final PitchDirection direction;
    private final List<Float> trace;
    private final double traceHz;

    public HoldReport(double startSec, double endSec, int refMidi, double stableSec, double inTuneShare, double toleranceCents,
                      double meanCents, double driftCents, double slopeCentsPerSec, double minCents, double maxCents,
                      PitchDirection direction, List<Float> trace, double traceHz) {
        this.startSec = startSec; this.endSec = endSec; this.refMidi = refMidi; this.stableSec = stableSec;
        this.inTuneShare = inTuneShare; this.toleranceCents = toleranceCents; this.meanCents = meanCents;
        this.driftCents = driftCents; this.slopeCentsPerSec = slopeCentsPerSec; this.minCents = minCents;
        this.maxCents = maxCents; this.direction = direction; this.trace = trace; this.traceHz = traceHz;
    }

    public double getStartSec() { return startSec; }
    public double getEndSec() { return endSec; }
    public int getRefMidi() { return refMidi; }
    public double getStableSec() { return stableSec; }
    public double getInTuneShare() { return inTuneShare; }
    public double getToleranceCents() { return toleranceCents; }
    public double getMeanCents() { return meanCents; }
    public double getDriftCents() { return driftCents; }
    public double getSlopeCentsPerSec() { return slopeCentsPerSec; }
    public double getMinCents() { return minCents; }
    public double getMaxCents() { return maxCents; }
    public PitchDirection getDirection() { return direction; }
    public List<Float> getTrace() { return trace; }
    public double getTraceHz() { return traceHz; }

    /** Seconds spent inside the tolerance. */
    public double getInTuneSec() { return stableSec * inTuneShare; }

    /** Pitch at the end of the stable part, relative to the reference (mean of the last five trace points). */
    public double getEndCents() {
        if (trace.isEmpty()) return meanCents;
        int from = Math.max(0, trace.size() - 5);
        double sum = 0;
        for (int i = from; i < trace.size(); i++) sum += trace.get(i);
        return sum / (trace.size() - from);
    }

    /** Share of trace points within ±{@code toleranceCents}; used to re-evaluate stored holds. */
    public static double inTuneShareAt(List<Float> trace, double toleranceCents) {
        if (trace.isEmpty()) return 0.0;
        int n = 0;
        for (Float c : trace) if (Math.abs(c) <= toleranceCents) n++;
        return n / (double) trace.size();
    }
}
