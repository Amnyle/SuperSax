package app.supersax.domain.song;

/**
 * A high-quality time stretch and pitch shift (the native Signalsmith Stretch on phones). Interleaved samples.
 * Processing follows the input with [inputLatency] frames of look-ahead; output lags by [outputLatency] frames.
 */
public interface StretchEngine {
    void reset();
    void setTranspose(float semitones, float tonalityLimit);
    int inputLatency();
    int outputLatency();
    /** Starts at a new place: [input] ends where processing should start (at least inputLatency frames). */
    void seek(float[] input, int frames, double playbackRate);
    /** [inFrames] of input in, [outFrames] of output out (their ratio is the speed). */
    void process(float[] input, int inFrames, float[] output, int outFrames);
    void release();
}
