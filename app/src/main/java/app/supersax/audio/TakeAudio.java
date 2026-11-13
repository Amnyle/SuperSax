package app.supersax.audio;

import android.annotation.SuppressLint;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import app.supersax.domain.song.SongRenderer;
import app.supersax.domain.take.ClockMap;
import app.supersax.domain.take.CountIn;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import org.jetbrains.annotations.Nullable;

/**
 * The audio of a video take: plays a 4-click count-in then the song (from a bar, at the chosen speed and key) on
 * its own AudioTrack while recording the microphone. Both streams are written to PCM files (the music exactly as
 * played, clicks left out) with clock maps from the platform timestamps, so the mix can place them on the
 * video's time line (see {@link app.supersax.domain.take.TakeMixer}).
 */
public final class TakeAudio {
    public static final int MIC_RATE = 48_000;
    private static final int BLOCK = 512;

    private final File songPcm;
    private final AudioDecoder.PcmInfo info;
    private final long startMs;
    private final float speed;
    private final int semitones;
    private final double countBpm;
    private final File musicOut, micOut;

    private volatile boolean running;
    private volatile int beat = -1;
    private volatile long songMs;
    private volatile boolean ended, done;
    private volatile @Nullable String error;
    private Thread playThread, micThread;

    public final ClockMap musicClock;
    public final ClockMap micClock = new ClockMap(MIC_RATE);
    /** Music frame (in the music file) where the song itself starts, after the count-in. */
    public final long songStartFrame;

    public TakeAudio(File songPcm, AudioDecoder.PcmInfo info, long startMs, float speed, int semitones, double countBpm, File musicOut, File micOut) {
        this.songPcm = songPcm; this.info = info; this.startMs = startMs; this.speed = speed; this.semitones = semitones;
        this.countBpm = countBpm > 0 ? countBpm : 100; this.musicOut = musicOut; this.micOut = micOut;
        this.musicClock = new ClockMap(info.rate);
        this.songStartFrame = CountIn.frames(info.rate, this.countBpm, 4);
    }

    /** Count-in beat being heard (1 to 4), 0 once the song plays, -1 before. */
    public int getBeat() { return beat; }
    public long getSongMs() { return songMs; }
    public boolean getEnded() { return ended; }
    /** The music has finished playing (song end heard, or an error); the take can be closed. */
    public boolean getDone() { return done; }
    public @Nullable String getError() { return error; }

    public void start() {
        running = true;
        micThread = new Thread(this::recordMic, "take-mic");
        micThread.setPriority(Thread.MAX_PRIORITY);
        micThread.start();
        playThread = new Thread(this::play, "take-play");
        playThread.setPriority(Thread.MAX_PRIORITY);
        playThread.start();
    }

    /** Stops both streams and waits for the files to be complete. */
    public void stop() {
        running = false;
        try { if (playThread != null) playThread.join(3000); if (micThread != null) micThread.join(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void header(RandomAccessFile f, int rate, int channels, long frames) throws IOException {
        f.seek(0);
        f.writeInt(AudioDecoder.PCM_MAGIC); f.writeInt(rate); f.writeInt(channels); f.writeLong(frames);
    }

    private static void writeShorts(DataOutputStream out, float[] buf, int n) throws IOException {
        for (int i = 0; i < n; i++) {
            int v = Math.round(Math.max(-1f, Math.min(1f, buf[i])) * 32767f);
            out.write(v & 0xff); out.write((v >> 8) & 0xff);
        }
    }

    private void play() {
        int ch = info.channels, rate = info.rate;
        RandomAccessFile src = null;
        SongRenderer r = null;
        AudioTrack track = null;
        long frames = 0;
        try (FileOutputStream fos = new FileOutputStream(musicOut); DataOutputStream out = new DataOutputStream(new BufferedOutputStream(fos, 1 << 16))) {
            out.write(new byte[AudioDecoder.PCM_HEADER]);
            src = new RandomAccessFile(songPcm, "r");
            MappedByteBuffer map = src.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, src.length());
            map.order(ByteOrder.LITTLE_ENDIAN);
            r = new SongRenderer(new SongEngine.PcmSource(map, info), NativeStretch.create(ch, rate));
            r.setSpeed(speed);
            r.setSemitones(semitones);
            r.seek(startMs);
            int mask = ch == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
            int min = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_FLOAT);
            track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(mask).build())
                .setBufferSizeInBytes(Math.max(min, BLOCK * ch * 4 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
            float[] count = CountIn.render(rate, ch, countBpm, 4);
            float[] silent = new float[BLOCK * ch];
            float[] buf = new float[BLOCK * ch];
            AudioTimestamp ts = new AudioTimestamp();
            long beatFrames = Math.round(60.0 / countBpm * rate);
            track.play();
            int countPos = 0;
            while (running) {
                boolean counting = countPos < count.length;
                if (counting) {
                    int n = Math.min(buf.length, count.length - countPos);
                    System.arraycopy(count, countPos, buf, 0, n);
                    if (n < buf.length) java.util.Arrays.fill(buf, n, buf.length, 0f);
                    countPos += n;
                    // The count-in goes to the ears but not into the video's music.
                    writeShorts(out, silent, buf.length);
                } else {
                    SongRenderer.Block b = r.render(buf, BLOCK);
                    writeShorts(out, buf, buf.length);
                    if (b.ended || b.stopped) ended = true;
                }
                track.write(buf, 0, buf.length, AudioTrack.WRITE_BLOCKING);
                frames += BLOCK;
                if (track.getTimestamp(ts)) musicClock.update(ts.framePosition, ts.nanoTime);
                // What is being heard now.
                double heard = musicClock.getKnown() ? musicClock.frameAt(System.nanoTime()) : frames - track.getBufferSizeInFrames();
                if (heard < songStartFrame) beat = heard < 0 ? -1 : (int) (heard / beatFrames) + 1;
                else { beat = 0; songMs = (long) (startMs + (heard - songStartFrame) * 1000.0 / rate * speed); }
                if (ended) break;
            }
            // Let the last queued audio play out before stopping.
            if (ended) {
                long until = System.nanoTime() + (long) (track.getBufferSizeInFrames() * 1e9 / rate) + 100_000_000L;
                while (running && System.nanoTime() < until) Thread.sleep(20);
            }
        } catch (Exception e) {
            error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            ended = true;
        } finally {
            if (track != null) { try { track.stop(); } catch (Exception ignored) { } track.release(); }
            if (r != null) r.release();
            done = true;
            if (src != null) { try { src.close(); } catch (IOException ignored) { } }
            try (RandomAccessFile f = new RandomAccessFile(musicOut, "rw")) { header(f, rate, ch, frames); } catch (IOException ignored) { }
        }
    }

    @SuppressLint("MissingPermission")
    private void recordMic() {
        int source = Build.VERSION.SDK_INT >= 29 ? MediaRecorder.AudioSource.VOICE_PERFORMANCE : MediaRecorder.AudioSource.MIC;
        int min = AudioRecord.getMinBufferSize(MIC_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord rec = null;
        long frames = 0;
        try (FileOutputStream fos = new FileOutputStream(micOut); BufferedOutputStream out = new BufferedOutputStream(fos, 1 << 16)) {
            out.write(new byte[AudioDecoder.PCM_HEADER]);
            try { rec = new AudioRecord(source, MIC_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 8192) * 2); }
            catch (Exception e) { rec = null; }
            if (rec == null || rec.getState() != AudioRecord.STATE_INITIALIZED) {
                if (rec != null) rec.release();
                rec = new AudioRecord(MediaRecorder.AudioSource.MIC, MIC_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 8192) * 2);
            }
            rec.startRecording();
            byte[] buf = new byte[2048];
            AudioTimestamp ts = new AudioTimestamp();
            while (running) {
                int n = rec.read(buf, 0, buf.length);
                if (n <= 0) continue;
                out.write(buf, 0, n);
                frames += n / 2;
                if (rec.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) micClock.update(ts.framePosition, ts.nanoTime);
                else if (!micClock.getKnown()) micClock.update(frames, System.nanoTime());
            }
        } catch (Exception e) {
            if (error == null) error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        } finally {
            if (rec != null) { try { rec.stop(); } catch (Exception ignored) { } rec.release(); }
            try (RandomAccessFile f = new RandomAccessFile(micOut, "rw")) { header(f, MIC_RATE, 1, frames); } catch (IOException ignored) { }
        }
    }

    /** Opens a PCM file written by a take (or the song cache) as a mixer source; close [file] when done. */
    public static SongRenderer.Source open(RandomAccessFile file) throws IOException {
        file.seek(0);
        if (file.readInt() != AudioDecoder.PCM_MAGIC) throw new IOException("not a PCM file");
        int rate = file.readInt(), ch = file.readInt();
        long frames = file.readLong();
        MappedByteBuffer map = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file.length());
        map.order(ByteOrder.LITTLE_ENDIAN);
        return new SongEngine.PcmSource(map, new AudioDecoder.PcmInfo(rate, ch, Math.min(frames, (file.length() - AudioDecoder.PCM_HEADER) / (2L * ch))));
    }
}
