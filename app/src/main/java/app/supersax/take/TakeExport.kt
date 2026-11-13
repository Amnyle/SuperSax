package app.supersax.take

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import app.supersax.domain.take.TakeMixer
import java.io.File
import java.nio.ByteBuffer

/** Turns a recorded take into a shareable MP4: the camera's video (copied, not re-encoded) with the mixed audio. */
object TakeExport {
    private const val AAC_BITRATE = 192_000

    data class VideoInfo(val durationMs: Long, val width: Int, val height: Int, val rotation: Int) {
        /** Width / height as seen (after rotation). */
        val aspect: Float get() = if (rotation % 180 == 0) width.toFloat() / height else height.toFloat() / width
    }

    fun info(video: File): VideoInfo? = runCatching {
        val m = MediaMetadataRetriever()
        try {
            m.setDataSource(video.path)
            VideoInfo(
                m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 16,
                m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 9,
                m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
            )
        } finally { m.release() }
    }.getOrNull()

    private class Sample(val data: ByteArray, val timeUs: Long, val flags: Int)

    /**
     * Writes [out]: the video between [fromMs] and [toMs] (starting at the key frame at or before [fromMs]) with
     * stereo AAC audio rendered by [mixer] on the same time line.
     */
    fun export(video: File, mixer: TakeMixer, fromMs: Long, toMs: Long, out: File, progress: (Float) -> Unit) {
        val vi = info(video) ?: error("cannot read the video")
        val ex = MediaExtractor()
        ex.setDataSource(video.path)
        val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
        val videoFormat = ex.getTrackFormat(track)
        ex.selectTrack(track)
        val firstUs = ex.sampleTime.coerceAtLeast(0)
        ex.seekTo(firstUs + fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val startUs = ex.sampleTime.coerceAtLeast(firstUs)
        val endUs = firstUs + toMs * 1000
        val totalUs = (endUs - startUs).coerceAtLeast(1)

        // Audio first (the muxer needs every track's format before it starts); the encoded take is small.
        val rate = mixer.rate
        val audio = ArrayList<Sample>()
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AAC_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 2 * 2 * 4)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var audioFormat: MediaFormat? = null
        try {
            enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            val startFrame = (startUs - firstUs) * rate / 1_000_000
            val totalFrames = totalUs * rate / 1_000_000
            var done = 0L
            var inputDone = false
            val block = FloatArray(1024 * 2)
            val bi = MediaCodec.BufferInfo()
            while (true) {
                if (!inputDone) {
                    val i = enc.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = enc.getInputBuffer(i)!!
                        val n = minOf(1024L, totalFrames - done).toInt()
                        if (n <= 0) {
                            enc.queueInputBuffer(i, 0, 0, done * 1_000_000 / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            mixer.render(startFrame + done, block, n)
                            buf.clear()
                            for (k in 0 until n * 2) {
                                val v = (block[k] * 32767f).toInt().coerceIn(-32768, 32767)
                                buf.put((v and 0xff).toByte()); buf.put((v shr 8 and 0xff).toByte())
                            }
                            enc.queueInputBuffer(i, 0, n * 4, done * 1_000_000 / rate, 0)
                            done += n
                            progress(0.8f * done / totalFrames)
                        }
                    }
                }
                val o = enc.dequeueOutputBuffer(bi, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) audioFormat = enc.outputFormat
                else if (o >= 0) {
                    val ob = enc.getOutputBuffer(o)!!
                    if (bi.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && bi.size > 0) {
                        val bytes = ByteArray(bi.size)
                        ob.position(bi.offset); ob.get(bytes)
                        audio += Sample(bytes, bi.presentationTimeUs, bi.flags)
                    }
                    enc.releaseOutputBuffer(o, false)
                    if (bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            runCatching { enc.stop() }
            enc.release()
        }

        val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            mux.setOrientationHint(vi.rotation)
            val vt = mux.addTrack(videoFormat)
            val at = mux.addTrack(audioFormat ?: error("no audio format"))
            mux.start()
            val size = if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 4 shl 20
            val buf = ByteBuffer.allocate(maxOf(size, 1 shl 20))
            val info = MediaCodec.BufferInfo()
            var ai = 0
            fun writeAudioUntil(us: Long) {
                while (ai < audio.size && audio[ai].timeUs <= us) {
                    val s = audio[ai++]
                    info.set(0, s.data.size, s.timeUs, s.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv())
                    mux.writeSampleData(at, ByteBuffer.wrap(s.data), info)
                }
            }
            while (true) {
                val t = ex.sampleTime
                if (t < 0 || t > endUs) break
                val n = ex.readSampleData(buf, 0)
                if (n < 0) break
                val pts = t - startUs
                writeAudioUntil(pts)
                @Suppress("WrongConstant")
                info.set(0, n, pts, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                mux.writeSampleData(vt, buf, info)
                progress(0.8f + 0.2f * pts / totalUs)
                ex.advance()
            }
            writeAudioUntil(Long.MAX_VALUE)
            mux.stop()
        } finally {
            mux.release()
            ex.release()
        }
        progress(1f)
    }

    /** Copies [file] into the gallery (Movies/Super Sax); returns the folder shown to the user. */
    fun saveToGallery(context: Context, file: File, name: String): String {
        val display = name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "Super Sax" } + ".mp4"
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, display)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Super Sax")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: error("cannot create $display")
            resolver.openOutputStream(uri)!!.use { o -> file.inputStream().use { it.copyTo(o) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Super Sax").apply { mkdirs() }
            val target = File(dir, display)
            file.copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf("video/mp4"), null)
        }
        return Environment.DIRECTORY_MOVIES + "/Super Sax"
    }

    fun shareIntent(context: Context, file: File, name: String): Intent {
        val dir = File(context.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val f = File(dir, name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "Super Sax" } + ".mp4")
        file.copyTo(f, overwrite = true)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null)
    }
}
