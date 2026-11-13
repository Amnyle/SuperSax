package app.supersax.audio;

import app.supersax.domain.song.StretchEngine;
import org.jetbrains.annotations.Nullable;

/** Signalsmith Stretch (MIT, C++, see src/main/cpp) through JNI. */
public final class NativeStretch implements StretchEngine {
    private static final boolean LOADED;
    static {
        boolean ok;
        try { System.loadLibrary("supersax_stretch"); ok = true; } catch (Throwable t) { ok = false; }
        LOADED = ok;
    }

    /** A stretcher for [channels] at [rate], or null where the native library is missing (tests, odd devices). */
    public static @Nullable NativeStretch create(int channels, float rate) {
        if (!LOADED) return null;
        try { return new NativeStretch(create0(channels, rate), channels); } catch (Throwable t) { return null; }
    }

    private long handle;
    private final int channels;
    private NativeStretch(long handle, int channels) { this.handle = handle; this.channels = channels; }

    @Override public synchronized void reset() { if (handle != 0) reset(handle); }
    @Override public synchronized void setTranspose(float semitones, float tonalityLimit) { if (handle != 0) setTranspose(handle, semitones, tonalityLimit); }
    @Override public synchronized int inputLatency() { return handle == 0 ? 0 : inputLatency(handle); }
    @Override public synchronized int outputLatency() { return handle == 0 ? 0 : outputLatency(handle); }
    @Override public synchronized void seek(float[] input, int frames, double rate) { if (handle != 0) seek(handle, input, frames, rate); }
    @Override public synchronized void process(float[] input, int inFrames, float[] output, int outFrames) {
        if (handle != 0) process(handle, input, inFrames, output, outFrames);
    }
    @Override public synchronized void release() { if (handle != 0) { destroy(handle); handle = 0; } }

    private static native long create0(int channels, float rate);
    private static native void destroy(long h);
    private static native void reset(long h);
    private static native void setTranspose(long h, float semitones, float tonalityLimit);
    private static native int inputLatency(long h);
    private static native int outputLatency(long h);
    private static native void seek(long h, float[] input, int frames, double rate);
    private static native void process(long h, float[] input, int inFrames, float[] output, int outFrames);
}
