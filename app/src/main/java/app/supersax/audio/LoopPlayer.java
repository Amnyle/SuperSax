package app.supersax.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import org.jetbrains.annotations.Nullable;

/** Plays a short rendered buffer (one blues chorus) in a seamless loop; reports where in the loop it is. */
public final class LoopPlayer {
    private @Nullable AudioTrack track;
    private int frames;
    private int rate;

    /** Starts looping [samples] (mono, -1..1) at [rate], replacing what was playing. */
    public synchronized void play(float[] samples, int rate, float volume) {
        stop();
        short[] pcm = new short[samples.length];
        for (int i = 0; i < samples.length; i++) pcm[i] = (short) Math.max(-32768, Math.min(32767, Math.round(samples[i] * 32767)));
        AudioTrack t = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.length * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build();
        t.write(pcm, 0, pcm.length);
        t.setLoopPoints(0, pcm.length, -1);
        t.setVolume(volume);
        t.play();
        track = t;
        frames = pcm.length;
        this.rate = rate;
    }

    public synchronized void setVolume(float v) { if (track != null) track.setVolume(v); }

    public synchronized boolean isPlaying() { return track != null; }

    /** Seconds into the loop being heard (0 when stopped). */
    public synchronized double positionSec() {
        if (track == null || frames == 0) return 0;
        return (Math.floorMod(track.getPlaybackHeadPosition(), frames)) / (double) rate;
    }

    public synchronized void stop() {
        if (track != null) {
            try { track.stop(); } catch (Exception ignored) { }
            track.release();
            track = null;
        }
    }
}
