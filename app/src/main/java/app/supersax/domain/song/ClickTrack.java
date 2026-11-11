package app.supersax.domain.song;

import java.util.ArrayList;
import java.util.List;

/**
 * Metronome clicks laid over a song: beats every 60000/bpm ms of the original recording from the
 * start of bar 1, beat 1 of each bar accented, nothing before bar 1. Works on song time, so clicks
 * follow any playback speed.
 */
public final class ClickTrack {
    private ClickTrack() {}

    /** A click at [songMs] (original time); [accent] on the first beat of a bar. */
    public static final class Click {
        private final double songMs;
        private final boolean accent;
        public Click(double songMs, boolean accent) { this.songMs = songMs; this.accent = accent; }
        public double getSongMs() { return songMs; }
        public boolean getAccent() { return accent; }
    }

    /** Clicks with song time in [fromMs, toMs). */
    public static List<Click> between(double fromMs, double toMs, double bpm, long firstBeatMs, int beatsPerBar) {
        List<Click> out = new ArrayList<>();
        if (bpm <= 0 || toMs <= fromMs) return out;
        double beat = 60000.0 / bpm;
        long k = (long) Math.max(0, Math.ceil((fromMs - firstBeatMs) / beat - 1e-9));
        for (double t = firstBeatMs + k * beat; t < toMs; k++, t = firstBeatMs + k * beat) {
            if (t >= fromMs) out.add(new Click(t, Math.floorMod(k, beatsPerBar) == 0));
        }
        return out;
    }
}
