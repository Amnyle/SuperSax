package app.supersax.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import app.supersax.domain.song.SongRenderer;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import org.jetbrains.annotations.Nullable;

/**
 * The song player's audio: plays a decoded song (cached 16-bit PCM file, memory-mapped) through a
 * {@link SongRenderer} on its own AudioTrack. Untouched samples at normal speed and key; a phase
 * vocoder for slower or faster playback; metronome clicks rendered into the same samples.
 * The API mirrors {@link PracticePlayer} so the screen drives it the same way.
 */
public final class SongEngine {
    private static final int BLOCK = 512;

    private final MutableStateFlow<PlayerUi> ui = StateFlowKt.MutableStateFlow(new PlayerUi());
    private @Nullable SongRenderer renderer;
    private @Nullable RandomAccessFile file;
    private volatile boolean playing, running;
    private volatile int semitones;
    private volatile float speed = 1f;
    private volatile @Nullable Long loopStart, loopEnd, stopAt;
    private volatile long heardMs;
    private volatile long durationMs;
    private Thread thread;

    private @Nullable VersionSource versions;
    private final java.util.List<RandomAccessFile> versionFiles = new java.util.ArrayList<>();

    /** A memory-mapped cached PCM file as renderer source. */
    static final class PcmSource implements SongRenderer.Source, VersionSource.Pcm {
        private final MappedByteBuffer map;
        private final AudioDecoder.PcmInfo info;
        PcmSource(MappedByteBuffer map, AudioDecoder.PcmInfo info) { this.map = map; this.info = info; }
        @Override public int channels() { return info.channels; }
        @Override public int sampleRate() { return info.rate; }
        @Override public long frames() { return info.frames; }
        @Override public float sample(int channel, long frame) {
            if (frame < 0 || frame >= info.frames) return 0f;
            return map.getShort((int) (AudioDecoder.PCM_HEADER + (frame * info.channels + channel) * 2)) / 32768f;
        }
    }

    public StateFlow<PlayerUi> getUi() { return ui; }

    /**
     * Adds another recording of this song (an AI version) that can be switched to without a gap; it is lined up
     * with the main one first. Returns its index, or -1 if nothing is open.
     */
    public int addVersion(File pcm, AudioDecoder.PcmInfo info) throws IOException {
        VersionSource vs = versions;
        if (vs == null) return -1;
        RandomAccessFile f = new RandomAccessFile(pcm, "r");
        MappedByteBuffer map = f.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, f.length());
        map.order(ByteOrder.LITTLE_ENDIAN);
        PcmSource v = new PcmSource(map, info);
        double offset = VersionSource.align(vs.versions0(), v);
        synchronized (this) {
            if (versions != vs) { f.close(); return -1; }
            versionFiles.add(f);
            return vs.add(v, offset);
        }
    }

    /** Switches to version [index] (0 = the song opened) at the place being played, with a short crossfade. */
    public void setVersion(int index) {
        VersionSource vs = versions;
        SongRenderer r = renderer;
        if (vs == null || r == null) return;
        long frame = (long) (r.positionMs() * vs.sampleRate() / 1000.0);
        vs.select(index, frame);
    }

    public int getVersion() { VersionSource vs = versions; return vs == null ? 0 : vs.current(); }

    public boolean isReady() { return renderer != null; }

    /** Opens a cached PCM file (see {@link AudioDecoder#decodeToPcm}). */
    public synchronized void open(File pcm, AudioDecoder.PcmInfo info) throws IOException {
        close();
        file = new RandomAccessFile(pcm, "r");
        MappedByteBuffer map = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file.length());
        map.order(ByteOrder.LITTLE_ENDIAN);
        VersionSource vs = new VersionSource(new PcmSource(map, info));
        versions = vs;
        SongRenderer r = new SongRenderer(vs, NativeStretch.create(info.channels, info.rate));
        r.setSpeed(speed);
        r.setSemitones(semitones);
        renderer = r;
        durationMs = (long) r.durationMs();
        heardMs = 0;
        running = true;
        final int rate = info.rate, channels = info.channels;
        thread = new Thread(() -> loop(r, rate, channels), "sax-song");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
        publish();
    }

    private synchronized void close() {
        running = false;
        playing = false;
        SongRenderer old = renderer;
        renderer = null;
        versions = null;
        if (old != null) {
            // Free the native stretcher once the audio thread has stopped using it.
            Thread t = thread;
            new Thread(() -> { if (t != null) { try { t.join(1000); } catch (InterruptedException ignored) { } } old.release(); }, "sax-song-free").start();
        }
        for (RandomAccessFile f : versionFiles) { try { f.close(); } catch (IOException ignored) { } }
        versionFiles.clear();
        if (file != null) { try { file.close(); } catch (IOException ignored) { } file = null; }
    }

    private void loop(SongRenderer r, int rate, int channels) {
        int mask = channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        int min = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_FLOAT);
        AudioTrack track;
        try {
            track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(mask).build())
                .setBufferSizeInBytes(Math.max(min, BLOCK * channels * 4 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        } catch (Exception e) {
            ui.setValue(ui.getValue().withError(e.getMessage() != null ? e.getMessage() : "AudioTrack"));
            return;
        }
        float[] buf = new float[BLOCK * channels];
        AudioTimestamp ts = new AudioTimestamp();
        long written = 0;
        boolean started = false, jumped = false;
        try {
            while (running && renderer == r) {
                if (!playing) {
                    if (started) {
                        // Resume exactly where it was heard (what was queued for the speaker is dropped).
                        if (!jumped) r.seek(heardMs);
                        track.pause(); track.flush(); started = false; written = 0; jumped = false;
                    }
                    heardMs = (long) r.positionMs();
                    Thread.sleep(10);
                    continue;
                }
                SongRenderer.Block b = r.render(buf, BLOCK);
                if (!started) { track.play(); started = true; }
                track.write(buf, 0, BLOCK * channels, AudioTrack.WRITE_BLOCKING);
                written += BLOCK;
                // The song position being heard: written frames minus those still queued, in song time.
                double queued = track.getTimestamp(ts) ? (written - ts.framePosition) : track.getBufferSizeInFrames();
                heardMs = (long) Math.max(0, b.songEndMs - Math.max(0, queued) * 1000.0 / rate * r.speed());
                if (b.stopped || b.ended) { playing = false; jumped = b.stopped; heardMs = (long) r.positionMs(); }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            ui.setValue(ui.getValue().withError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } finally {
            try { track.stop(); } catch (Exception ignored) { }
            track.release();
        }
    }

    /** Copies the engine state into the UI state (call regularly from the main thread). */
    public void tick() { publish(); }

    private void publish() {
        PlayerUi u = ui.getValue();
        ui.setValue(new PlayerUi(playing, heardMs, durationMs, speed, loopStart, loopEnd, u.getError(), semitones, stopAt));
    }

    public void play() {
        SongRenderer r = renderer;
        if (r == null) return;
        if (r.positionMs() >= durationMs - 50) r.seek(loopStart != null ? loopStart : 0);
        playing = true;
        publish();
    }

    public void pause() { playing = false; publish(); }

    public void toggle() { if (playing) pause(); else play(); }

    public void seekTo(long ms) {
        SongRenderer r = renderer;
        if (r == null) return;
        long t = Math.max(0, Math.min(durationMs, ms));
        r.seek(t);
        VersionSource vs = versions;
        if (vs != null) vs.settle();
        heardMs = t;
        publish();
    }

    public long position() { return heardMs; }

    public void setSpeed(float s) {
        speed = Math.max(0.25f, Math.min(2f, s));
        SongRenderer r = renderer;
        if (r != null) r.setSpeed(speed);
        publish();
    }

    public void setSemitones(int n) {
        semitones = Math.max(-12, Math.min(12, n));
        SongRenderer r = renderer;
        if (r != null) r.setSemitones(semitones);
        publish();
    }

    public void setLoop(@Nullable Long startMs, @Nullable Long endMs) {
        loopStart = startMs; loopEnd = endMs;
        SongRenderer r = renderer;
        if (r != null) r.setLoop(startMs, endMs);
        publish();
    }

    public void setStopAt(@Nullable Long ms, @Nullable Long thenSeekTo) {
        stopAt = ms;
        SongRenderer r = renderer;
        if (r != null) r.setStopAt(ms, thenSeekTo);
        publish();
    }

    public void setVolume(float v) { SongRenderer r = renderer; if (r != null) r.setMusicGain(v); }

    /** Metronome clicks from bar 1 at the song's tempo (original time), with their volume. */
    public void setClicks(boolean on, float gain, double bpm, long firstBeatMs, int beatsPerBar) {
        SongRenderer r = renderer;
        if (r != null) r.setClicks(on, gain, bpm, firstBeatMs, beatsPerBar);
    }

    public void release() { close(); }
}
