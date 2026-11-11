package app.supersax.audio;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.DoubleConsumer;
import org.jetbrains.annotations.Nullable;

/**
 * Decodes an audio file (MP3, AAC/M4A, Opus/WebM, WAV...) with the platform codecs into mono float
 * samples at {@link #RATE} Hz, for key and tempo analysis. At most {@link #MAX_SECONDS} are kept.
 */
public final class AudioDecoder {
    private AudioDecoder() {}

    public static final int RATE = 22050;
    public static final int MAX_SECONDS = 600;

    /** Mono samples at {@link #RATE}, or null if the file has no decodable audio track. */
    public static @Nullable float[] decode(Context context, Uri uri, DoubleConsumer progress) throws IOException {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try {
            ex.setDataSource(context, uri, null);
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { track = i; format = f; break; }
            }
            if (track < 0) return null;
            ex.selectTrack(track);
            long durationUs = format.containsKey(MediaFormat.KEY_DURATION) ? format.getLong(MediaFormat.KEY_DURATION) : 0;
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();

            int srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            boolean floatPcm = false;
            Resampler out = new Resampler(srcRate, RATE, (int) ((long) RATE * MAX_SECONDS));
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false;
            while (!outputDone && !out.full()) {
                if (!inputDone) {
                    int in = codec.dequeueInputBuffer(10_000);
                    if (in >= 0) {
                        ByteBuffer buf = codec.getInputBuffer(in);
                        int size = buf == null ? -1 : ex.readSampleData(buf, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long t = ex.getSampleTime();
                            codec.queueInputBuffer(in, 0, size, t, 0);
                            if (durationUs > 0) progress.accept(Math.min(1.0, (double) t / durationUs));
                            ex.advance();
                        }
                    }
                }
                int o = codec.dequeueOutputBuffer(info, 10_000);
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat f = codec.getOutputFormat();
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) out.setSourceRate(f.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT;
                } else if (o >= 0) {
                    ByteBuffer buf = codec.getOutputBuffer(o);
                    if (buf != null && info.size > 0) {
                        buf.position(info.offset).limit(info.offset + info.size);
                        ByteBuffer le = buf.slice().order(ByteOrder.nativeOrder());
                        if (floatPcm) {
                            int frames = info.size / 4 / channels;
                            for (int i = 0; i < frames; i++) {
                                float s = 0;
                                for (int ch = 0; ch < channels; ch++) s += le.getFloat((i * channels + ch) * 4);
                                out.push(s / channels);
                            }
                        } else {
                            int frames = info.size / 2 / channels;
                            for (int i = 0; i < frames; i++) {
                                float s = 0;
                                for (int ch = 0; ch < channels; ch++) s += le.getShort((i * channels + ch) * 2) / 32768f;
                                out.push(s / channels);
                            }
                        }
                    }
                    codec.releaseOutputBuffer(o, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
            progress.accept(1.0);
            return out.result();
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) { }
                codec.release();
            }
            ex.release();
        }
    }

    /** Linear-interpolation resampler into a growing buffer (enough for tempo and key analysis). */
    public static final class Resampler {
        private double step;
        private final int max;
        private float[] buf = new float[1 << 16];
        private int size;
        private double pos;
        private float prev;
        private long index;

        public Resampler(int srcRate, int dstRate, int max) { this.step = (double) srcRate / dstRate; this.max = max; }

        public void setSourceRate(int srcRate) { step = (double) srcRate / RATE; }

        public boolean full() { return size >= max; }

        public void push(float s) {
            // Emit every output sample whose time falls between the previous input sample and this one.
            while (pos <= index && size < max) {
                double frac = 1 - (index - pos);
                float v = (float) (prev + (s - prev) * Math.max(0, Math.min(1, frac)));
                if (size == buf.length) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
                buf[size++] = v;
                pos += step;
            }
            prev = s;
            index++;
        }

        public float[] result() { return java.util.Arrays.copyOf(buf, size); }
    }
}
