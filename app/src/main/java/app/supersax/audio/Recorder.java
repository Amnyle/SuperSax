package app.supersax.audio;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.SystemClock;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.Nullable;

/** Records the microphone to an AAC .m4a file in app-private storage. */
public final class Recorder {
    private final Context context;
    private @Nullable MediaRecorder recorder;
    private long startedAt;
    private @Nullable File file;

    public Recorder(Context context) { this.context = context; }

    public @Nullable File getFile() { return file; }

    public boolean getRecording() { return recorder != null; }

    /** Starts recording into target. Throws if the microphone cannot be opened. */
    @SuppressWarnings("deprecation")
    public void start(File target) throws IOException {
        stopQuietly();
        MediaRecorder r = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(context) : new MediaRecorder();
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioSamplingRate(44_100);
            r.setAudioEncodingBitRate(128_000);
            r.setOutputFile(target.getAbsolutePath());
            r.prepare();
            r.start();
        } catch (IOException | RuntimeException e) {
            r.release();
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw e;
        }
        recorder = r;
        file = target;
        startedAt = SystemClock.elapsedRealtime();
    }

    /** Stops and returns the duration in ms, or null if nothing usable was recorded. */
    public @Nullable Long stop() {
        MediaRecorder r = recorder;
        if (r == null) return null;
        long duration = SystemClock.elapsedRealtime() - startedAt;
        recorder = null;
        try {
            r.stop();
            return duration;
        } catch (RuntimeException e) {
            if (file != null) //noinspection ResultOfMethodCallIgnored
                file.delete();
            return null;
        } finally {
            r.release();
        }
    }

    public long elapsedMs() { return recorder != null ? SystemClock.elapsedRealtime() - startedAt : 0; }

    private void stopQuietly() {
        try { stop(); } catch (RuntimeException ignored) { }
    }
}
