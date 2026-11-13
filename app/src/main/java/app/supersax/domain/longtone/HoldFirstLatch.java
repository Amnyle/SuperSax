package app.supersax.domain.longtone;

import org.jetbrains.annotations.Nullable;

/**
 * "Hold first": waits for the player's first steady note and keeps it as the drone's root. A note counts once
 * the same semitone has been heard for [steadySec] without a gap longer than [gapSec].
 */
public final class HoldFirstLatch {
    private final double steadySec, gapSec;
    private @Nullable Integer latched;
    private @Nullable Integer candidate;
    private double since = -1, lastSeen = -1;

    public HoldFirstLatch(double steadySec, double gapSec) { this.steadySec = steadySec; this.gapSec = gapSec; }
    public HoldFirstLatch() { this(0.6, 0.15); }

    public @Nullable Integer getLatched() { return latched; }

    public void reset() { latched = null; candidate = null; since = -1; lastSeen = -1; }

    /** Feeds one frame (nearest concert MIDI, null when silent); returns the latched note once there is one. */
    public @Nullable Integer push(double timeSec, @Nullable Integer midi) {
        if (latched != null) return latched;
        if (midi == null) {
            if (candidate != null && timeSec - lastSeen > gapSec) { candidate = null; since = -1; }
            return null;
        }
        if (candidate == null || !candidate.equals(midi) || timeSec - lastSeen > gapSec) { candidate = midi; since = timeSec; }
        lastSeen = timeSec;
        if (timeSec - since >= steadySec) latched = candidate;
        return latched;
    }
}
