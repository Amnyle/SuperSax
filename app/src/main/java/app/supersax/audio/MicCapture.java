package app.supersax.audio;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import app.supersax.domain.audio.PitchFrame;
import app.supersax.domain.audio.PitchTracker;
import app.supersax.domain.audio.Yin;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.channels.BufferOverflow;
import kotlinx.coroutines.flow.MutableSharedFlow;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.SharedFlow;
import kotlinx.coroutines.flow.SharedFlowKt;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import org.jetbrains.annotations.Nullable;

/**
 * Captures the microphone with AudioRecord and turns it into pitch frames with the pure YIN
 * PitchTracker. Audio is analysed live and never stored.
 */
public final class MicCapture {
    private static final int SAMPLE_RATE = 44_100;

    private final MutableSharedFlow<PitchFrame> frames = SharedFlowKt.MutableSharedFlow(0, 64, BufferOverflow.DROP_OLDEST);
    private final MutableStateFlow<MicState> state = StateFlowKt.MutableStateFlow((MicState) MicState.Idle.INSTANCE);
    /** Raw samples for the experimental scale checker (optional listener). */
    private volatile @Nullable Function2<float[], Integer, Unit> rawListener;

    private volatile @Nullable Thread thread;
    private volatile boolean running;
    private int users;

    public SharedFlow<PitchFrame> getFrames() { return frames; }
    public StateFlow<MicState> getState() { return state; }
    public @Nullable Function2<float[], Integer, Unit> getRawListener() { return rawListener; }
    public void setRawListener(@Nullable Function2<float[], Integer, Unit> l) { rawListener = l; }

    /** Starts listening (reference counted). The caller must hold RECORD_AUDIO. */
    public synchronized void start(boolean hasPermission) {
        if (!hasPermission) { state.setValue(new MicState.Failed(MicState.Reason.NO_PERMISSION)); return; }
        users++;
        if (running) return;
        running = true;
        Thread t = new Thread(this::loop, "sax-mic");
        thread = t;
        t.start();
    }

    public synchronized void stop() {
        users = Math.max(0, users - 1);
        if (users == 0) running = false;
    }

    /** Retries after a failure. */
    public void retry(boolean hasPermission) {
        synchronized (this) { users = 0; running = false; }
        Thread t = thread;
        if (t != null) {
            try { t.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        start(hasPermission);
    }

    @SuppressLint("MissingPermission") // checked by the caller, reported as NO_PERMISSION otherwise
    private void loop() {
        int minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT);
        int size = Math.max(minBytes, 8192);
        AudioRecord record;
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, size);
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, size);
            }
        } catch (SecurityException e) {
            fail(MicState.Reason.NO_PERMISSION); return;
        } catch (Exception e) {
            fail(MicState.Reason.ERROR); return;
        }
        if (record.getState() != AudioRecord.STATE_INITIALIZED) { record.release(); fail(MicState.Reason.BUSY); return; }
        PitchTracker tracker = new PitchTracker(new Yin(SAMPLE_RATE));
        float[] buf = new float[512];
        try {
            record.startRecording();
            if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) { fail(MicState.Reason.BUSY); return; }
            state.setValue(MicState.Listening.INSTANCE);
            while (running) {
                int n = record.read(buf, 0, buf.length, AudioRecord.READ_BLOCKING);
                if (n < 0) { fail(MicState.Reason.ERROR); break; }
                Function2<float[], Integer, Unit> l = rawListener;
                if (l != null) l.invoke(buf, n);
                for (PitchFrame f : tracker.push(buf, n)) frames.tryEmit(f);
            }
        } catch (Exception e) {
            fail(MicState.Reason.ERROR);
        } finally {
            try { record.stop(); } catch (Exception ignored) { }
            record.release();
            if (state.getValue() == MicState.Listening.INSTANCE) state.setValue(MicState.Idle.INSTANCE);
        }
    }

    private void fail(MicState.Reason reason) {
        running = false;
        synchronized (this) { users = 0; }
        state.setValue(new MicState.Failed(reason));
    }
}
