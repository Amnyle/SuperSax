package app.supersax.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.os.SystemClock;
import app.supersax.domain.song.ClickTrack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Metronome clicks over a playing song, on their own audio stream. The song player reports where the
 * song is ({@link #sync}); this thread renders the clicks of the song time it is about to play, aimed
 * with the measured output latency, and keeps its own continuous song clock between reports (it only
 * jumps after a seek, a pause or a drift above 30 ms), so no click is doubled or dropped.
 */
public final class ClickOverlay {
    private static final int RATE = 44_100;
    private static final int BLOCK = 256;

    private final float[] accentClick = click(2000.0, 0.9f), beatClick = click(1400.0, 0.6f);

    // Latest report from the player (written on the main thread, read by the audio thread).
    private volatile double reportedSongMs;
    private volatile long reportedAtMs;
    private volatile float speed = 1f;
    private volatile boolean playing;
    private volatile double bpm;
    private volatile long firstBeatMs;
    private volatile int beatsPerBar = 4;
    private volatile boolean enabled;
    private volatile float gain = 0.7f;
    private volatile boolean running;
    private Thread thread;

    private static float[] click(double freq, float amp) {
        int n = (int) (0.035 * RATE);
        float[] c = new float[n];
        for (int i = 0; i < n; i++) c[i] = (float) (amp * Math.sin(2 * Math.PI * freq * i / RATE) * Math.exp(-i / (0.008 * RATE)));
        return c;
    }

    public void setEnabled(boolean on) { enabled = on; ensureRunning(); }
    public boolean isEnabled() { return enabled; }
    public void setGain(float g) { gain = Math.max(0f, Math.min(1f, g)); }

    /** Where the song is now (original time), how fast it plays, whether it plays, and its bars. */
    public void sync(long songMs, float speed, boolean playing, double bpm, long firstBeatMs, int beatsPerBar) {
        this.reportedSongMs = songMs;
        this.reportedAtMs = SystemClock.elapsedRealtime();
        this.speed = speed;
        this.playing = playing;
        this.bpm = bpm;
        this.firstBeatMs = firstBeatMs;
        this.beatsPerBar = beatsPerBar;
        ensureRunning();
    }

    private synchronized void ensureRunning() {
        if (running || !enabled) return;
        running = true;
        thread = new Thread(this::loop, "sax-click-overlay");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    public synchronized void release() {
        running = false;
        enabled = false;
    }

    /** One sounding click: which sample of it plays next. */
    private static final class Voice {
        final float[] samples;
        int at;
        Voice(float[] samples, int startOffset) { this.samples = samples; this.at = -startOffset; }
    }

    private void loop() {
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
        AudioTrack track;
        try {
            track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(Math.max(min, BLOCK * 4 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build();
        } catch (Exception e) {
            running = false;
            return;
        }
        float[] buf = new float[BLOCK];
        List<Voice> voices = new ArrayList<>();
        AudioTimestamp ts = new AudioTimestamp();
        long written = 0;
        double nextSongMs = Double.NaN; // song time of the next frame we write
        try {
            track.play();
            while (running) {
                if (!enabled || !playing || bpm <= 0) {
                    voices.clear();
                    nextSongMs = Double.NaN;
                    Thread.sleep(15);
                    continue;
                }
                float sp = speed;
                // Latency: frames written but not yet heard, from the track's timestamp when available.
                double latencyMs;
                if (track.getTimestamp(ts)) {
                    latencyMs = (written - ts.framePosition) * 1000.0 / RATE - (System.nanoTime() - ts.nanoTime) / 1e6;
                } else {
                    latencyMs = track.getBufferSizeInFrames() * 1000.0 / RATE;
                }
                double expected = reportedSongMs + (SystemClock.elapsedRealtime() - reportedAtMs) * sp + Math.max(0, latencyMs) * sp;
                if (Double.isNaN(nextSongMs) || Math.abs(expected - nextSongMs) > 30) { nextSongMs = expected; voices.clear(); }
                double blockSongMs = BLOCK * 1000.0 / RATE * sp;
                for (ClickTrack.Click c : ClickTrack.between(nextSongMs, nextSongMs + blockSongMs, bpm, firstBeatMs, beatsPerBar)) {
                    int offset = (int) Math.round((c.getSongMs() - nextSongMs) / sp * RATE / 1000.0);
                    voices.add(new Voice(c.getAccent() ? accentClick : beatClick, Math.max(0, Math.min(BLOCK - 1, offset))));
                }
                Arrays.fill(buf, 0f);
                float g = gain;
                for (int v = voices.size() - 1; v >= 0; v--) {
                    Voice voice = voices.get(v);
                    for (int i = 0; i < BLOCK; i++) {
                        int k = voice.at + i;
                        if (k >= 0 && k < voice.samples.length) buf[i] += voice.samples[k] * g;
                    }
                    voice.at += BLOCK;
                    if (voice.at >= voice.samples.length) voices.remove(v);
                }
                track.write(buf, 0, BLOCK, AudioTrack.WRITE_BLOCKING);
                written += BLOCK;
                nextSongMs += blockSongMs;
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // A failing output only silences the clicks; the song keeps playing.
        } finally {
            try { track.stop(); } catch (Exception ignored) { }
            track.release();
            running = false;
        }
    }
}
