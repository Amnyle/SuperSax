package app.supersax.separation

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Client of the LALAL.AI stem separation service (www.lalal.ai/api): upload a song, ask for one instrument to be
 * split out, poll until done, then download the "back track" (the song without that instrument). Every request
 * carries the user's licence key ("Authorization: license <key>"). Errors from the service are passed on as text.
 */
object Lalal {
    var base = "https://www.lalal.ai/api/"
    private val json = Json { ignoreUnknownKeys = true }

    /** The instruments the service can take out, with their API names. */
    enum class Stem(val api: String) { WIND("wind"), VOCALS("vocals"), PIANO("piano"), GUITAR("electric_guitar"), DRUMS("drum"), BASS("bass") }

    class ServiceException(message: String) : Exception(message)

    data class Status(val state: String, val progress: Int, val backTrack: String?, val stemTrack: String?, val error: String?)

    private fun open(path: String, key: String): HttpURLConnection =
        (URL(base + path).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "license $key")
            connectTimeout = 30_000; readTimeout = 120_000
        }

    private fun read(c: HttpURLConnection): JsonObject {
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream ?: c.inputStream).bufferedReader().use { it.readText() }
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw ServiceException("HTTP $code: ${text.take(200)}")
        val status = (obj["status"] as? JsonPrimitive)?.content
        if (status != null && status != "success") throw ServiceException((obj["error"] as? JsonPrimitive)?.content ?: "error")
        if (code !in 200..299) throw ServiceException("HTTP $code")
        return obj
    }

    /** Uploads the audio and returns the service's file id. */
    fun upload(key: String, input: InputStream, fileName: String, length: Long = -1): String {
        val c = open("upload/", key)
        c.requestMethod = "POST"
        c.doOutput = true
        val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        c.setRequestProperty("Content-Disposition", "attachment; filename=\"$safe\"; filename*=UTF-8''" + URLEncoder.encode(fileName, "UTF-8").replace("+", "%20"))
        if (length > 0) c.setFixedLengthStreamingMode(length) else c.setChunkedStreamingMode(64 * 1024)
        c.outputStream.use { out -> input.use { it.copyTo(out, 64 * 1024) } }
        val obj = read(c)
        return obj["id"]?.jsonPrimitive?.content ?: throw ServiceException("no file id")
    }

    /** Starts separating [stem] out of the uploaded file [id]. */
    fun split(key: String, id: String, stem: Stem) {
        val params = JsonArray(listOf(buildJsonObject { put("id", id); put("stem", stem.api) }))
        val body = "params=" + URLEncoder.encode(params.toString(), "UTF-8")
        val c = open("split/", key)
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.outputStream.use { it.write(body.toByteArray()) }
        read(c)
    }

    /** Where the separation of file [id] is. */
    fun check(key: String, id: String): Status {
        val c = open("check/", key)
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.outputStream.use { it.write(("id=" + URLEncoder.encode(id, "UTF-8")).toByteArray()) }
        return parseCheck(read(c), id)
    }

    internal fun parseCheck(obj: JsonObject, id: String): Status {
        val r = (obj["result"] as? JsonObject)?.get(id) as? JsonObject ?: return Status("progress", 0, null, null, null)
        val task = r["task"] as? JsonObject
        val split = r["split"] as? JsonObject
        val err = (r["error"] as? JsonPrimitive)?.content ?: (task?.get("error") as? JsonPrimitive)?.content
        val state = when {
            split != null && split["back_track"] != null -> "success"
            err != null || (task?.get("state") as? JsonPrimitive)?.content == "error" -> "error"
            else -> (task?.get("state") as? JsonPrimitive)?.content ?: "progress"
        }
        val progress = (task?.get("progress") as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: 0
        return Status(state, progress, (split?.get("back_track") as? JsonPrimitive)?.content, (split?.get("stem_track") as? JsonPrimitive)?.content, err)
    }

    /** Downloads a result to [to]. */
    fun download(url: String, to: File, onProgress: (Double) -> Unit = {}) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 30_000; c.readTimeout = 120_000
        val total = c.contentLengthLong
        c.inputStream.use { input ->
            to.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    if (total > 0) onProgress(done.toDouble() / total)
                }
            }
        }
    }
}
