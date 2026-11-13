package app.supersax.domain.take;

/**
 * Links frame counts of an audio stream to the monotonic clock (ns), from (frame, time) pairs reported by the
 * platform (AudioTrack / AudioRecord timestamps). The pair kept is the latest one, the rate is the stream's own.
 */
public final class ClockMap {
    private final int rate;
    private long frame = -1, nanos;

    public ClockMap(int rate) { this.rate = rate; }
    public ClockMap(int rate, long frame, long nanos) { this.rate = rate; this.frame = frame; this.nanos = nanos; }

    public int getRate() { return rate; }
    public boolean getKnown() { return frame >= 0; }
    public long getFrame() { return frame; }
    public long getNanos() { return nanos; }

    public synchronized void update(long frame, long nanos) { this.frame = frame; this.nanos = nanos; }

    /** When [f] is (or was) heard / captured. */
    public synchronized double nanosAt(double f) { return nanos + (f - frame) * 1e9 / rate; }

    /** The frame heard / captured at [t] ns (may be fractional, negative or past the end). */
    public synchronized double frameAt(double t) { return frame + (t - nanos) * rate / 1e9; }
}
