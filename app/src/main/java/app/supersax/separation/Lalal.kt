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
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Client of the LALAL.AI stem separation API v1 (www.lalal.ai/api/v1, OpenAPI at /api/v1/openapi.json): upload a
 * song, start a stem separation task, poll it, then download the "back" track (the song without that stem).
 * Every request carries the user's licence key in the X-License-Key header. Errors from the service are passed on.
 */
object Lalal {
    var base = "https://www.lalal.ai/api/v1/"
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * What can be taken out, with the API stem and model. Sax and other winds only exist on the older phoenix
     * model; voice/lead melody, piano, guitar, drums and bass use the newest model ("auto" = andromeda today).
     */
    enum class Stem(val api: String, val splitter: String) {
        WIND("wind", "phoenix"), LEAD("vocals", "auto"), PIANO("piano", "auto"), GUITAR("guitar", "auto"), DRUMS("drum", "auto"), BASS("bass", "auto")
    }

    class ServiceException(message: String) : Exception(message)

    data class Status(val state: String, val progress: Int, val backTrack: String?, val stemTrack: String?, val error: String?)

    private fun open(path: String, key: String): HttpURLConnection =
        (URL(base + path).openConnection() as HttpURLConnection).apply {
            setRequestProperty("X-License-Key", key)
            setRequestProperty("Accept", "application/json")
            connectTimeout = 30_000; readTimeout = 120_000
        }

    /** The message of an error body: {"detail": "..."} (or a list of details), else the raw text. */
    internal fun errorText(text: String): String {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return text.take(200)
        return when (val d = obj["detail"]) {
            is JsonPrimitive -> d.content
            is JsonArray -> d.joinToString("; ") { e -> ((e as? JsonObject)?.get("msg") as? JsonPrimitive)?.content ?: e.toString() }
            is JsonObject -> d.toString()
            else -> (obj["error"] as? JsonPrimitive)?.content ?: text.take(200)
        }
    }

    private fun read(c: HttpURLConnection): JsonObject {
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream ?: c.inputStream).bufferedReader().use { it.readText() }
        if (code !in 200..299) throw ServiceException(errorText(text).ifBlank { "HTTP $code" })
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: throw ServiceException("HTTP $code: ${text.take(200)}")
    }

    private fun postJson(path: String, key: String, body: JsonObject): JsonObject {
        val c = open(path, key)
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        return read(c)
    }

    /** Uploads the audio and returns its source id. */
    fun upload(key: String, input: InputStream, fileName: String, length: Long = -1): String {
        val c = open("upload/", key)
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/octet-stream")
        val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        c.setRequestProperty("Content-Disposition", "attachment; filename=\"$safe\"; filename*=UTF-8''" + URLEncoder.encode(fileName, "UTF-8").replace("+", "%20"))
        if (length > 0) c.setFixedLengthStreamingMode(length) else c.setChunkedStreamingMode(64 * 1024)
        c.outputStream.use { out -> input.use { it.copyTo(out, 64 * 1024) } }
        val obj = read(c)
        return (obj["id"] as? JsonPrimitive)?.content ?: throw ServiceException("no source id")
    }

    /** Starts separating [stem] out of source [sourceId]; returns the task id. Results come back as mp3. */
    fun split(key: String, sourceId: String, stem: Stem): String {
        val body = buildJsonObject {
            put("source_id", sourceId)
            putJsonObject("presets") {
                put("stem", stem.api)
                put("splitter", stem.splitter)
                put("encoder_format", "mp3")
                // Take as much of the instrument as possible: the back track is what we keep.
                if (stem.splitter != "auto") put("extraction_level", "deep_extraction")
            }
        }
        val obj = postJson("split/stem_separator/", key, body)
        return (obj["task_id"] as? JsonPrimitive)?.content ?: throw ServiceException("no task id")
    }

    /** Where task [taskId] is. */
    fun check(key: String, taskId: String): Status =
        parseCheck(postJson("check/", key, buildJsonObject { putJsonArray("task_ids") { add(JsonPrimitive(taskId)) } }), taskId)

    internal fun parseCheck(obj: JsonObject, taskId: String): Status {
        val r = (obj["result"] as? JsonObject)?.get(taskId) as? JsonObject ?: return Status("progress", 0, null, null, null)
        val status = (r["status"] as? JsonPrimitive)?.content ?: "progress"
        val progress = (r["progress"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: 0
        val err = when (val e = r["error"]) {
            is JsonObject -> (e["detail"] as? JsonPrimitive)?.content ?: e.toString()
            is JsonPrimitive -> e.content
            else -> null
        }
        val tracks = ((r["result"] as? JsonObject)?.get("tracks") as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        fun url(t: JsonObject) = (t["url"] as? JsonPrimitive)?.content
        fun type(t: JsonObject) = (t["type"] as? JsonPrimitive)?.content
        fun label(t: JsonObject) = (t["label"] as? JsonPrimitive)?.content ?: ""
        val backs = tracks.filter { type(it) == "back" }
        // For vocals there can be two back tracks: "no_vocals" (instrumental) and "mix_no_lead"; take the instrumental.
        val back = backs.firstOrNull { label(it).startsWith("no_") } ?: backs.firstOrNull()
        val stemTrack = tracks.firstOrNull { type(it) == "stem" }
        val state = when (status) {
            "success" -> if (back != null) "success" else "error"
            "error", "server_error", "cancelled" -> "error"
            else -> "progress"
        }
        return Status(state, progress, back?.let(::url), stemTrack?.let(::url), err ?: if (status == "cancelled") "cancelled" else null)
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
