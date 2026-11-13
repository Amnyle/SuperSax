package app.supersax.audio;

import app.supersax.domain.song.SongRenderer;

/**
 * Several recordings of the same song (the original and its AI versions) behind one renderer source, so the
 * player can switch between them without a gap: at the frame being read, the old and new versions are
 * crossfaded over 30 ms (equal power). Each version is kept in time with the first one by an offset in its own
 * frames (MP3 versions often start a few milliseconds late) and read at the first one's sample rate.
 */
public final class VersionSource implements SongRenderer.Source {
    /** One recording: its samples and how its frames map onto the main one's (f' = f × ratio + offset). */
    public interface Pcm {
        int channels();
        int sampleRate();
        long frames();
        float sample(int channel, long frame);
    }

    private volatile Pcm[] versions;
    private volatile double[] ratio;
    private volatile double[] offset;
    private volatile int current, previous;
    private volatile long switchAt = Long.MIN_VALUE;
    private final long fade;

    public VersionSource(Pcm main) {
        versions = new Pcm[]{main};
        ratio = new double[]{1};
        offset = new double[]{0};
        fade = Math.max(1, main.sampleRate() * 30L / 1000);
    }

    @Override public int channels() { return versions[0].channels(); }
    @Override public int sampleRate() { return versions[0].sampleRate(); }
    @Override public long frames() { return versions[0].frames(); }

    /** Adds a version, [offsetFrames] (its own frames) late against the main one; returns its index. */
    public synchronized int add(Pcm v, double offsetFrames) {
        int n = versions.length;
        Pcm[] vs = java.util.Arrays.copyOf(versions, n + 1);
        double[] r = java.util.Arrays.copyOf(ratio, n + 1);
        double[] o = java.util.Arrays.copyOf(offset, n + 1);
        vs[n] = v;
        r[n] = (double) v.sampleRate() / versions[0].sampleRate();
        o[n] = offsetFrames;
        versions = vs; ratio = r; offset = o;
        return n;
    }

    public int count() { return versions.length; }
    public Pcm versions0() { return versions[0]; }
    public int current() { return current; }

    /** From frame [atFrame] on, version [index] is heard (crossfaded in from the current one). */
    public synchronized void select(int index, long atFrame) {
        if (index < 0 || index >= versions.length || index == current) return;
        previous = current;
        switchAt = atFrame;
        current = index;
    }

    /** After a seek: no fade to finish, the current version everywhere. */
    public void settle() { previous = current; }

    private float read(int i, int channel, long frame) {
        Pcm v = versions[i];
        double pos = frame * ratio[i] + offset[i];
        long k = (long) Math.floor(pos);
        double a = pos - k;
        int ch = Math.min(channel, v.channels() - 1);
        float s0 = v.sample(ch, k), s1 = a == 0 ? s0 : v.sample(ch, k + 1);
        return (float) (s0 + (s1 - s0) * a);
    }

    @Override public float sample(int channel, long frame) {
        int c = current, p = previous;
        if (c == p) return read(c, channel, frame);
        long s = switchAt;
        if (frame >= s + fade) return read(c, channel, frame);
        if (frame < s) return read(p, channel, frame);
        double x = (double) (frame - s) / fade * Math.PI / 2;
        return (float) (read(p, channel, frame) * Math.cos(x) + read(c, channel, frame) * Math.sin(x));
    }

    /**
     * How many of [v]'s frames it is late against [main] (same song, maybe one instrument less): the lag that best
     * lines up the two recordings, found on a loudness envelope over ±400 ms, then refined on the samples (±2 ms).
     */
    public static double align(Pcm main, Pcm v) {
        int rate = main.sampleRate();
        double ratio = (double) v.sampleRate() / rate;
        long total = Math.min(main.frames(), (long) (v.frames() / ratio));
        if (total < rate * 4L) return 0;
        long start = Math.min(total / 4, rate * 10L);
        int len = (int) Math.min(rate * 20L, total - start - rate);
        // Envelope at 1 kHz.
        int hop = Math.max(1, rate / 1000);
        int en = len / hop;
        double[] a = new double[en], b = new double[en];
        for (int i = 0; i < en; i++) {
            double sa = 0, sb = 0;
            for (int j = 0; j < hop; j++) {
                long f = start + (long) i * hop + j;
                sa += Math.abs(main.sample(0, f));
                sb += Math.abs(v.sample(0, (long) (f * ratio)));
            }
            a[i] = sa; b[i] = sb;
        }
        removeMean(a); removeMean(b);
        int maxLag = 400, best = 0;
        double bestC = -Double.MAX_VALUE;
        for (int lag = -maxLag; lag <= maxLag; lag++) {
            double c = 0;
            for (int i = maxLag; i < en - maxLag; i++) c += a[i] * b[i + lag];
            if (c > bestC) { bestC = c; best = lag; }
        }
        // Refine on the samples around the coarse lag (in main frames).
        long coarse = (long) best * hop;
        int fine = Math.max(2, rate / 500), n = Math.min(len, rate * 2);
        long bestFine = coarse;
        bestC = -Double.MAX_VALUE;
        for (long lag = coarse - fine; lag <= coarse + fine; lag++) {
            double c = 0;
            for (int i = 0; i < n; i++) {
                long f = start + i;
                c += main.sample(0, f) * v.sample(0, (long) ((f + lag) * ratio));
            }
            if (c > bestC) { bestC = c; bestFine = lag; }
        }
        return bestFine * ratio;
    }

    private static void removeMean(double[] x) {
        double m = 0;
        for (double d : x) m += d;
        m /= Math.max(1, x.length);
        for (int i = 0; i < x.length; i++) x[i] -= m;
    }
}
