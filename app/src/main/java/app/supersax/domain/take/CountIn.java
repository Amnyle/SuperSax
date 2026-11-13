package app.supersax.domain.take;

/**
 * The count-in before the first bar: [beats] clicks at [bpm] (the playback tempo), the first one accented; the
 * song starts one beat after the last click. Rendered into an interleaved buffer.
 */
public final class CountIn {
    private CountIn() { }

    public static long frames(int rate, double bpm, int beats) { return Math.round(beats * 60.0 / bpm * rate); }

    public static float[] render(int rate, int channels, double bpm, int beats) {
        long n = frames(rate, bpm, beats);
        float[] out = new float[(int) n * channels];
        int clickLen = (int) (0.03 * rate);
        for (int b = 0; b < beats; b++) {
            int at = (int) Math.round(b * 60.0 / bpm * rate);
            double hz = b == 0 ? 1600 : 1100;
            for (int i = 0; i < clickLen && at + i < n; i++) {
                double env = Math.exp(-i / (0.006 * rate));
                float s = (float) (0.6 * env * Math.sin(2 * Math.PI * hz * i / rate));
                for (int c = 0; c < channels; c++) out[(at + i) * channels + c] = s;
            }
        }
        return out;
    }
}
