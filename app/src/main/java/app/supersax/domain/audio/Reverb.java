package app.supersax.domain.audio;

/**
 * A small stereo room (the classic "Freeverb" design: eight damped comb filters and four all-pass filters per side,
 * the right side slightly longer so the two differ). Process one mono send sample at a time.
 */
public final class Reverb {
    private static final int[] COMBS = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
    private static final int[] ALLPASS = {556, 441, 341, 225};
    private static final int SPREAD = 23;

    private final float[][] combL, combR, apL, apR;
    private final int[] ciL, ciR, aiL, aiR;
    private final float[] storeL, storeR;
    private final float feedback, damp;

    /** [room] 0–1 (decay length), [damping] 0–1 (darker tail when higher). */
    public Reverb(int rate, double room, double damping) {
        double s = rate / 44100.0;
        combL = new float[COMBS.length][]; combR = new float[COMBS.length][];
        ciL = new int[COMBS.length]; ciR = new int[COMBS.length];
        storeL = new float[COMBS.length]; storeR = new float[COMBS.length];
        for (int i = 0; i < COMBS.length; i++) {
            combL[i] = new float[Math.max(1, (int) (COMBS[i] * s))];
            combR[i] = new float[Math.max(1, (int) ((COMBS[i] + SPREAD) * s))];
        }
        apL = new float[ALLPASS.length][]; apR = new float[ALLPASS.length][];
        aiL = new int[ALLPASS.length]; aiR = new int[ALLPASS.length];
        for (int i = 0; i < ALLPASS.length; i++) {
            apL[i] = new float[Math.max(1, (int) (ALLPASS[i] * s))];
            apR[i] = new float[Math.max(1, (int) ((ALLPASS[i] + SPREAD) * s))];
        }
        feedback = (float) (0.7 + 0.28 * room);
        damp = (float) (0.4 * damping);
    }

    /** One sample of send in; [out] receives the wet left and right samples. */
    public void process(float in, float[] out) {
        float x = in * 0.015f;
        float l = 0, r = 0;
        for (int i = 0; i < COMBS.length; i++) {
            l += comb(combL[i], ciL, storeL, i, x);
            r += comb(combR[i], ciR, storeR, i, x);
        }
        for (int i = 0; i < ALLPASS.length; i++) {
            l = allpass(apL[i], aiL, i, l);
            r = allpass(apR[i], aiR, i, r);
        }
        out[0] = l; out[1] = r;
    }

    private float comb(float[] buf, int[] idx, float[] store, int i, float x) {
        int k = idx[i];
        float y = buf[k];
        store[i] = y * (1 - damp) + store[i] * damp;
        buf[k] = x + store[i] * feedback;
        idx[i] = k + 1 == buf.length ? 0 : k + 1;
        return y;
    }

    private static float allpass(float[] buf, int[] idx, int i, float x) {
        int k = idx[i];
        float b = buf[k];
        float y = -x + b;
        buf[k] = x + b * 0.5f;
        idx[i] = k + 1 == buf.length ? 0 : k + 1;
        return y;
    }
}
