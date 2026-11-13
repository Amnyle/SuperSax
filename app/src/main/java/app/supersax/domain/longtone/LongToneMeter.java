package app.supersax.domain.longtone;

import org.jetbrains.annotations.Nullable;

/**
 * Live numbers for a long tone: how long the current note has been held, the share of it in tune, and the
 * loudness. A hold starts on the first voiced frame and ends after [gapSec] of silence or a change of
 * semitone; the best hold of the session is kept.
 */
public final class LongToneMeter {
    private final double toleranceCents, gapSec;
    private double start = -1, last = -1;
    private int frames, inTune;
    private @Nullable Integer note;
    private double best;

    public LongToneMeter(double toleranceCents, double gapSec) { this.toleranceCents = toleranceCents; this.gapSec = gapSec; }
    public LongToneMeter() { this(10, 0.3); }

    /** Seconds of the current hold (0 when not holding). */
    public double getHoldSec() { return start < 0 ? 0 : last - start; }
    /** Share of voiced frames of the current hold within tolerance, 0 to 100; null before any frame. */
    public @Nullable Integer getInTunePercent() { return frames == 0 ? null : (int) Math.round(100.0 * inTune / frames); }
    public double getBestSec() { return best; }
    public boolean getHolding() { return start >= 0; }

    public void reset() { start = -1; last = -1; frames = 0; inTune = 0; note = null; best = 0; }

    /** One frame: time, nearest semitone (null when silent) and cents off. */
    public void push(double timeSec, @Nullable Integer midi, double cents) {
        if (midi == null) {
            if (start >= 0 && timeSec - last > gapSec) end();
            return;
        }
        if (start >= 0 && (!midi.equals(note) || timeSec - last > gapSec)) end();
        if (start < 0) { start = timeSec; frames = 0; inTune = 0; note = midi; }
        last = timeSec;
        frames++;
        if (Math.abs(cents) <= toleranceCents) inTune++;
        best = Math.max(best, last - start);
    }

    private void end() { start = -1; last = -1; note = null; }

    /** Loudness in dB full scale from an RMS level (silence clamps to -90). */
    public static double decibels(double rms) { return rms <= 0 ? -90 : Math.max(-90, 20 * Math.log10(rms)); }
}
