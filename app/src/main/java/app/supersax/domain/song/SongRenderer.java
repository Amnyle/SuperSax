package app.supersax.domain.song;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a song block by block for the song player: at normal speed and key the original samples
 * pass through untouched; otherwise a {@link TimeStretcher} changes the duration (speed) and a cubic
 * resampler shifts the pitch. Loops, "stop at the end of the part" and metronome clicks are applied
 * here, in the same samples, so clicks can never drift from the music.
 *
 * <p>Thread use: setters may be called from the UI thread; {@link #render} runs on the audio thread.
 */
public final class SongRenderer {
    /** The decoded song: interleaving is the source's business; frames outside read as 0. */
    public interface Source extends TimeStretcher.Source {
        int channels();
        int sampleRate();
        long frames();
    }

    /** What happened in a block. */
    public static final class Block {
        public double songStartMs, songEndMs;
        /** The end of the part (stop mode) or of the song was reached: the player should pause. */
        public boolean stopped;
        public boolean ended;
    }

    private final Source src;
    private final int channels;
    private final double rate;
    private final TimeStretcher stretcher;

    // Settings (written by the UI thread).
    private volatile float speed = 1f;
    private volatile int semitones;
    private volatile @Nullable Long loopStartMs, loopEndMs, stopAtMs, returnToMs;
    private volatile float musicGain = 1f, clickGain = 0.7f;
    private volatile boolean clicks;
    private volatile double bpm;
    private volatile long firstBeatMs;
    private volatile int beatsPerBar = 4;
    private volatile double seekRequestMs = -1;

    // Audio-thread state.
    private double songPos; // frames of the original recording at the start of the next output frame
    private boolean stretching;
    private double analysisPos;
    private float[][] stretched = new float[0][0];
    private int stretchedFrames;
    private double readPos;
    private final List<double[]> voices = new ArrayList<>(); // {sampleIndex (negative = not started), accent}
    private final float[] accentClick, beatClick;

    public SongRenderer(Source src) {
        this.src = src;
        this.channels = src.channels();
        this.rate = src.sampleRate();
        this.stretcher = new TimeStretcher(channels);
        accentClick = click(2000.0, 0.9f);
        beatClick = click(1400.0, 0.6f);
    }

    private float[] click(double freq, float amp) {
        int n = (int) (0.035 * rate);
        float[] c = new float[n];
        for (int i = 0; i < n; i++) c[i] = (float) (amp * Math.sin(2 * Math.PI * freq * i / rate) * Math.exp(-i / (0.008 * rate)));
        return c;
    }

    // --- settings -------------------------------------------------------------------------------

    public void setSpeed(float s) { speed = Math.max(0.25f, Math.min(2f, s)); }
    public void setSemitones(int n) { semitones = Math.max(-12, Math.min(12, n)); }
    public void setLoop(@Nullable Long startMs, @Nullable Long endMs) { loopStartMs = startMs; loopEndMs = endMs; }
    public void setStopAt(@Nullable Long ms, @Nullable Long thenMs) { stopAtMs = ms; returnToMs = thenMs; }
    public void setMusicGain(float g) { musicGain = Math.max(0f, Math.min(1f, g)); }
    public void setClicks(boolean on, float gain, double bpm, long firstBeatMs, int beatsPerBar) {
        this.clicks = on; this.clickGain = Math.max(0f, Math.min(1f, gain)); this.bpm = bpm; this.firstBeatMs = firstBeatMs; this.beatsPerBar = beatsPerBar;
    }
    /** Jumps to [ms] before the next block. */
    public void seek(long ms) { seekRequestMs = Math.max(0, ms); }

    public double durationMs() { return src.frames() * 1000.0 / rate; }
    /** Song position (ms of the original) at the start of the next block. */
    public double positionMs() { double s = seekRequestMs; return s >= 0 ? s : songPos * 1000.0 / rate; }
    public float speed() { return speed; }

    private void jump(double ms) {
        songPos = ms * rate / 1000.0;
        stretching = false;
        voices.clear();
    }

    // --- rendering ------------------------------------------------------------------------------

    /** Fills [out] (interleaved, frames × channels) with the next block. */
    public Block render(float[] out, int frames) {
        Block b = new Block();
        double req = seekRequestMs;
        if (req >= 0) { seekRequestMs = -1; jump(req); }
        float sp = speed;
        int st = semitones;
        double startPos = songPos;
        b.songStartMs = startPos * 1000.0 / rate;
        Arrays.fill(out, 0, frames * channels, 0f);
        if (songPos >= src.frames()) { b.ended = true; b.songEndMs = b.songStartMs; return b; }

        if (sp == 1f && st == 0) {
            stretching = false;
            long p0 = (long) Math.floor(songPos);
            for (int i = 0; i < frames; i++) for (int c = 0; c < channels; c++) out[i * channels + c] = src.sample(c, p0 + i);
            songPos += frames;
        } else {
            double pitch = Math.pow(2, st / 12.0);
            double stretch = pitch / sp; // output duration / input duration before resampling
            if (!stretching) {
                stretcher.reset();
                analysisPos = songPos;
                stretchedFrames = 0;
                readPos = 0;
                stretching = true;
            }
            double ha = TimeStretcher.HOP / stretch;
            for (int i = 0; i < frames; i++) {
                while (stretchedFrames < (int) Math.floor(readPos) + 3) {
                    ensureCapacity(stretchedFrames + TimeStretcher.HOP);
                    stretcher.synthesize(src, analysisPos, stretched, stretchedFrames);
                    stretchedFrames += TimeStretcher.HOP;
                    analysisPos += ha;
                }
                int k = (int) Math.floor(readPos);
                float t = (float) (readPos - k);
                for (int c = 0; c < channels; c++) {
                    float[] s = stretched[c];
                    float y0 = k > 0 ? s[k - 1] : s[k], y1 = s[k], y2 = s[k + 1], y3 = s[k + 2];
                    out[i * channels + c] = cubic(y0, y1, y2, y3, t);
                }
                readPos += pitch;
            }
            // Drop what was read, keep one frame of history for the interpolation.
            int drop = Math.max(0, (int) Math.floor(readPos) - 1);
            if (drop > 0) {
                for (int c = 0; c < channels; c++) System.arraycopy(stretched[c], drop, stretched[c], 0, stretchedFrames - drop);
                stretchedFrames -= drop;
                readPos -= drop;
            }
            songPos += frames * (double) sp;
        }
        b.songEndMs = songPos * 1000.0 / rate;

        float mg = musicGain;
        if (mg != 1f) for (int i = 0; i < frames * channels; i++) out[i] *= mg;
        if (clicks && bpm > 0) addClicks(out, frames, b.songStartMs, b.songEndMs);

        // Loop and stop: checked at block ends (a block is about 12 ms).
        Long ls = loopStartMs, le = loopEndMs, stop = stopAtMs, ret = returnToMs;
        if (stop != null && b.songStartMs < stop && b.songEndMs >= stop) {
            b.stopped = true;
            jump(ret != null ? ret : stop);
        } else if (ls != null && le != null && le > ls && b.songEndMs >= le) {
            jump(ls);
        } else if (songPos >= src.frames()) {
            b.ended = true;
        }
        return b;
    }

    private void addClicks(float[] out, int frames, double fromMs, double toMs) {
        double span = toMs - fromMs;
        if (span > 0) {
            for (ClickTrack.Click c : ClickTrack.between(fromMs, toMs, bpm, firstBeatMs, beatsPerBar)) {
                int offset = (int) Math.round((c.getSongMs() - fromMs) / span * frames);
                voices.add(new double[]{-offset, c.getAccent() ? 1 : 0});
            }
        }
        float g = clickGain;
        for (int v = voices.size() - 1; v >= 0; v--) {
            double[] voice = voices.get(v);
            float[] samples = voice[1] > 0 ? accentClick : beatClick;
            int at = (int) voice[0];
            for (int i = 0; i < frames; i++) {
                int k = at + i;
                if (k < 0 || k >= samples.length) continue;
                float s = samples[k] * g;
                for (int c = 0; c < channels; c++) out[i * channels + c] += s;
            }
            voice[0] = at + frames;
            if (voice[0] >= samples.length) voices.remove(v);
        }
    }

    private void ensureCapacity(int frames) {
        if (stretched.length == channels && stretched[0].length >= frames) return;
        int size = Math.max(frames, TimeStretcher.HOP * 8);
        float[][] n = new float[channels][size * 2];
        for (int c = 0; c < channels && stretched.length == channels; c++) System.arraycopy(stretched[c], 0, n[c], 0, stretchedFrames);
        stretched = n;
    }

    /** Catmull-Rom interpolation between y1 and y2. */
    private static float cubic(float y0, float y1, float y2, float y3, float t) {
        float a = -0.5f * y0 + 1.5f * y1 - 1.5f * y2 + 0.5f * y3;
        float b = y0 - 2.5f * y1 + 2f * y2 - 0.5f * y3;
        float c = -0.5f * y0 + 0.5f * y2;
        return ((a * t + b) * t + c) * t + y1;
    }
}
