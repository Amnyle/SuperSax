package app.supersax.domain.take;

import app.supersax.domain.song.SongRenderer;

/**
 * Mixes a recorded take: the music that was played (what went to the speaker / headphones) and the microphone,
 * each placed on the video's time line through its {@link ClockMap}, so the sax lines up with the music as it
 * was heard in the room. [nudgeMs] moves the music against the sax and picture (positive = music later).
 * Output is stereo at the music's rate; the mic is mono, linearly resampled, normalised by [micScale].
 */
public final class TakeMixer {
    private final SongRenderer.Source music, mic;
    private final ClockMap musicClock, micClock;
    private final long videoStartNanos;
    private final int rate;
    private float musicGain = 1f, saxGain = 1f, micScale = 1f;
    private double nudgeMs;

    public TakeMixer(SongRenderer.Source music, ClockMap musicClock, SongRenderer.Source mic, ClockMap micClock, long videoStartNanos) {
        this.music = music; this.mic = mic; this.musicClock = musicClock; this.micClock = micClock;
        this.videoStartNanos = videoStartNanos;
        this.rate = music.sampleRate();
    }

    public int getRate() { return rate; }

    /** Balance from 0 (music only) to 1 (sax only); 0.5 keeps both at full level. */
    public void setBalance(float b) {
        b = Math.max(0f, Math.min(1f, b));
        musicGain = Math.min(1f, 2f * (1f - b));
        saxGain = Math.min(1f, 2f * b);
    }
    public void setNudgeMs(double ms) { nudgeMs = ms; }
    public void setMicScale(float s) { micScale = s; }

    /** A gain that brings the mic's loudest moment to about -3 dB (at most ×12, never louder than needed). */
    public static float normalise(SongRenderer.Source mic) {
        float peak = 0f;
        long n = mic.frames();
        for (long i = 0; i < n; i++) peak = Math.max(peak, Math.abs(mic.sample(0, i)));
        if (peak < 1e-4f) return 1f;
        return Math.min(12f, 0.7f / peak);
    }

    /** Length of the time line covered by both the video ([videoMs]) and the music, in output frames. */
    public long frames(double videoMs) { return (long) (videoMs / 1000.0 * rate); }

    /** Renders [count] stereo frames starting at output frame [start] (time 0 = first video frame). */
    public void render(long start, float[] out, int count) {
        int mch = music.channels();
        double step = 1e9 / rate;
        for (int i = 0; i < count; i++) {
            double t = videoStartNanos + (start + i) * step;
            float l = 0f, r = 0f;
            if (musicGain > 0f) {
                double f = musicClock.frameAt(t - nudgeMs * 1e6);
                long k = (long) Math.floor(f);
                if (k >= 0 && k < music.frames()) {
                    l = music.sample(0, k) * musicGain;
                    r = (mch > 1 ? music.sample(1, k) : music.sample(0, k)) * musicGain;
                }
            }
            if (saxGain > 0f) {
                double f = micClock.frameAt(t);
                long k = (long) Math.floor(f);
                if (k >= 0 && k + 1 < mic.frames()) {
                    float a = (float) (f - k);
                    float s = (mic.sample(0, k) * (1 - a) + mic.sample(0, k + 1) * a) * micScale * saxGain;
                    l += s; r += s;
                }
            }
            out[2 * i] = soft(l);
            out[2 * i + 1] = soft(r);
        }
    }

    /** Leaves normal levels untouched and rounds off peaks above 0.8 instead of clipping. */
    static float soft(float x) {
        float a = Math.abs(x);
        if (a <= 0.8f) return x;
        float y = 0.8f + 0.2f * (float) Math.tanh((a - 0.8f) / 0.2f);
        return Math.copySign(y, x);
    }
}
