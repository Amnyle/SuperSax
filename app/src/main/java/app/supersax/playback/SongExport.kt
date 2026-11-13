package app.supersax.playback

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import app.supersax.domain.common.AudioRefRecord
import java.io.File

/**
 * Saves a song's audio file to the phone's Download folder, or shares it (WhatsApp, Discord…), as it is: YouTube
 * downloads are M4A (AAC), AI versions MP3, imported files keep their own format.
 */
object SongExport {
    /** The file's extension: from its name, else from its type (content links), else "m4a". */
    fun extension(context: Context, uri: Uri): String {
        val fromName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast('.', "")?.lowercase()
        if (!fromName.isNullOrEmpty() && fromName.length <= 5 && fromName.all { it.isLetterOrDigit() }) return fromName
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        return mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "m4a"
    }

    fun mime(ext: String): String = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "audio/*"

    /** A file name from the song title, without characters file systems refuse. */
    fun fileName(title: String, ext: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().take(120).ifEmpty { "song" } + "." + ext

    /** Saves to Download/Super Sax (Android 10 and newer); returns the saved file's name. */
    @androidx.annotation.RequiresApi(29)
    fun saveToDownloads(context: Context, ref: AudioRefRecord): String {
        val src = Uri.parse(ref.uri)
        val ext = extension(context, src)
        val name = fileName(ref.title, ext)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime(ext))
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Super Sax")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val target = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: error("cannot create $name")
        try {
            copy(context, src, target)
            resolver.update(target, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(target, null, null); throw e
        }
        return name
    }

    /** Copies the song into a place the user chose (older Android, or "save as…"). */
    fun copy(context: Context, from: Uri, to: Uri) {
        val r = context.contentResolver
        (r.openInputStream(from) ?: error("cannot read the song")).use { input ->
            (r.openOutputStream(to) ?: error("cannot write the file")).use { input.copyTo(it, 64 * 1024) }
        }
    }

    /** The share sheet for the song's file, named after the song. */
    fun shareIntent(context: Context, ref: AudioRefRecord): Intent {
        val src = Uri.parse(ref.uri)
        val ext = extension(context, src)
        val uri = if (src.scheme == "file") {
            // Shared under the song's title: a copy in the cache, served by the app's file provider.
            val dir = File(context.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val f = File(dir, fileName(ref.title, ext))
            File(src.path!!).copyTo(f, overwrite = true)
            FileProvider.getUriForFile(context, context.packageName + ".files", f)
        } else src
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime(ext)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, ref.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, ref.title)
    }
}
