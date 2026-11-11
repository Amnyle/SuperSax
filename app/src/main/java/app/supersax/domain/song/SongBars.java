package app.supersax.domain.song;

/** Bar positions from a tempo, the start of bar 1 and the beats per bar (all in the original recording's time). */
public final class SongBars {
    private SongBars() {}

    public static double barMs(double bpm, int beatsPerBar) { return beatsPerBar * 60000.0 / bpm; }

    /** Start of bar [n] (1-based). */
    public static long barStartMs(int n, double bpm, long firstBeatMs, int beatsPerBar) {
        return firstBeatMs + Math.round((n - 1) * barMs(bpm, beatsPerBar));
    }

    /** Bar at [ms]: 1 from the first beat on; 0 before it (pickup or intro). */
    public static int barAt(long ms, double bpm, long firstBeatMs, int beatsPerBar) {
        if (ms < firstBeatMs) return 0;
        return (int) Math.floor((ms - firstBeatMs) / barMs(bpm, beatsPerBar) + 1e-6) + 1;
    }

    /** Number of bars that start before [durationMs]. */
    public static int barCount(long durationMs, double bpm, long firstBeatMs, int beatsPerBar) {
        if (durationMs <= firstBeatMs) return 0;
        return (int) Math.ceil((durationMs - firstBeatMs) / barMs(bpm, beatsPerBar));
    }
}
