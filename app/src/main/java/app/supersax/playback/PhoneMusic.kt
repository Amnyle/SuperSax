package app.supersax.playback

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

/** An audio file found on the phone. */
data class PhoneTrack(val id: Long, val uri: String, val title: String, val artist: String?, val album: String?, val folder: String?, val durationMs: Long)

/**
 * Searches all the audio on the phone through Android's media index, which covers every folder and subfolder of
 * the phone's storage and SD card (music, downloads, recordings…).
 */
object PhoneMusic {
    val permission: String get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun allowed(context: Context) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Files whose title, artist, album, file name or folder contain every word of [query] (up to [limit]). */
    fun search(context: Context, query: String, limit: Int = 60): List<PhoneTrack> {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || !allowed(context)) return emptyList()
        val folderCol = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
        val cols = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DISPLAY_NAME, folderCol, MediaStore.Audio.Media.DURATION)
        val fields = listOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DISPLAY_NAME, folderCol)
        // Each word must appear in one of the fields.
        val selection = words.joinToString(" AND ") { "(" + fields.joinToString(" OR ") { f -> "$f LIKE ?" } + ")" }
        val args = words.flatMap { w -> List(fields.size) { "%$w%" } }.toTypedArray()
        val base = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val out = mutableListOf<PhoneTrack>()
        context.contentResolver.query(base, cols, selection, args, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE")?.use { cur ->
            while (cur.moveToNext() && out.size < limit) {
                val id = cur.getLong(0)
                val name = cur.getString(4)
                out += PhoneTrack(
                    id, ContentUris.withAppendedId(base, id).toString(),
                    cur.getString(1)?.takeIf { it.isNotBlank() } ?: name?.substringBeforeLast('.') ?: "audio",
                    cur.getString(2)?.takeIf { it.isNotBlank() && it != "<unknown>" }, cur.getString(3)?.takeIf { it.isNotBlank() },
                    cur.getString(5)?.let { f -> if (Build.VERSION.SDK_INT >= 29) f.trimEnd('/') else f.substringBeforeLast('/') },
                    cur.getLong(6),
                )
            }
        }
        return out
    }
}
