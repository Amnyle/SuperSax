package app.supersax.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Process;
import app.supersax.domain.solo.Groove;
import org.jetbrains.annotations.Nullable;

/**
 * Plays a rendered backing loop seamlessly, mixing its parts live so each part's volume can change while it plays;
 * reports where in the loop it is.
 */
public final class LoopPlayer {
    private @Nullable AudioTrack track;
    private @Nullable Thread thread;
    private volatile boolean running;
    private volatile float[] gains = {1, 1, 1, 1};
    private int frames;
    private int rate;

    /** Starts looping [mix] from its beginning, replacing what was playing. */
    public synchronized void play(Groove.Mix mix, float[] partGains) {
        stop();
        gains = partGains.clone();
        rate = mix.rate;
        frames = mix.frames();
        int min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack t = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(Math.max(min * 2, rate / 5 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build();
        track = t;
        running = true;
        Thread th = new Thread(() -> loop(t, mix), "backing");
        thread = th;
        t.play();
        th.start();
    }

    private void loop(AudioTrack t, Groove.Mix mix) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        int block = 1024;
        float[] f = new float[block];
        short[] pcm = new short[block];
        float[] cur = gains.clone();
        int pos = 0;
        while (running) {
            int n = Math.min(block, frames - pos);
            float[] target = gains;
            // Glide to new gains over one block so changes do not click.
            float[] start = cur.clone();
            for (int i = 0; i < n; i++) {
                float a = (float) i / n;
                float v = 0;
                for (int p = 0; p < mix.stems.length; p++) v += mix.stems[p][pos + i] * (start[p] + (target[p] - start[p]) * a);
                f[i] = (float) Math.tanh(v * 1.1) * 0.92f;
            }
            cur = target.clone();
            for (int i = 0; i < n; i++) pcm[i] = (short) Math.round(f[i] * 32767);
            if (t.write(pcm, 0, n) < 0) break;
            pos += n;
            if (pos >= frames) pos = 0;
        }
    }

    /** Volume of each part (Groove.Part order), 0..1. */
    public void setGains(float[] g) { gains = g.clone(); }

    public synchronized boolean isPlaying() { return track != null; }

    /** Seconds into the loop being heard (0 when stopped). */
    public synchronized double positionSec() {
        if (track == null || frames == 0) return 0;
        return Math.floorMod(track.getPlaybackHeadPosition(), frames) / (double) rate;
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            thread = null;
        }
        if (track != null) {
            try { track.pause(); track.flush(); track.stop(); } catch (Exception ignored) { }
            track.release();
            track = null;
        }
    }
}
