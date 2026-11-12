package app.supersax.separation

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Client of MVSEP (mvsep.com/full_api): one multipart POST uploads the song and starts the separation
 * (`separation/create`, api_token, sep_type, add_opt…), then `separation/get?hash=` is polled until "done", and the
 * "Other" file (the song without the separated instrument) is downloaded. Errors from the service are passed on.
 */
object Mvsep {
    var base = "https://mvsep.com/api/"
    private val json = Json { ignoreUnknownKeys = true }

    /** A separation type: MVSEP's sep_type (render_id) and its options. */
    enum class Model(val sepType: Int, val options: Map<String, String>) {
        /** MVSep Saxophone, BS Roformer (SDR saxophone 9.77). Outputs saxophone + other. */
        SAXOPHONE(61, mapOf("add_opt1" to "3")),
        /** MVSep Wind, BS Roformer (SDR wind 9.82), extracted directly from the mix. Outputs wind + other. */
        WIND(54, mapOf("add_opt1" to "3", "add_opt2" to "0")),
    }

    data class Status(val state: String, val message: String?, val backTrack: String?)

    private fun read(c: HttpURLConnection): JsonObject {
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream ?: c.inputStream).bufferedReader().use { it.readText() }
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw Lalal.ServiceException(if (code == 401) "invalid API token" else "HTTP $code: ${text.take(200)}")
        val ok = (obj["success"] as? JsonPrimitive)?.content
        if (code !in 200..299 || ok == "false") {
            val msg = ((obj["data"] as? JsonObject)?.get("message") as? JsonPrimitive)?.content
                ?: (obj["message"] as? JsonPrimitive)?.content ?: (obj["errors"]?.toString()) ?: "HTTP $code"
            throw Lalal.ServiceException(msg)
        }
        return obj
    }

    /** Uploads the song and starts [model]; returns the job hash. */
    fun create(token: String, input: InputStream, fileName: String, model: Model): String {
        val boundary = "----supersax" + UUID.randomUUID().toString().replace("-", "")
        val c = (URL(base + "separation/create").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 30_000; readTimeout = 300_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
            setChunkedStreamingMode(64 * 1024)
        }
        c.outputStream.use { out ->
            fun field(name: String, value: String) {
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            }
            field("api_token", token)
            field("sep_type", model.sepType.toString())
            model.options.forEach { (k, v) -> field(k, v) }
            field("output_format", "0") // mp3 320 kbps
            val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"audiofile\"; filename=\"$safe\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
            input.use { it.copyTo(out, 64 * 1024) }
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val obj = read(c)
        return ((obj["data"] as? JsonObject)?.get("hash") as? JsonPrimitive)?.content ?: throw Lalal.ServiceException("no job hash")
    }

    /** Where job [hash] is: "progress", "success" (with the back track) or "error". */
    fun get(hash: String): Status {
        val c = (URL(base + "separation/get?hash=" + URLEncoder.encode(hash, "UTF-8")).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000; readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream ?: c.inputStream).bufferedReader().use { it.readText() }
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: throw Lalal.ServiceException("HTTP $code: ${text.take(200)}")
        return parseGet(obj)
    }

    internal fun parseGet(obj: JsonObject): Status {
        val status = (obj["status"] as? JsonPrimitive)?.content ?: "waiting"
        val data = obj["data"] as? JsonObject
        val message = (data?.get("message") as? JsonPrimitive)?.content
        return when (status) {
            "done" -> {
                val files = (data?.get("files") as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
                val other = files.firstOrNull { (it["type"] as? JsonPrimitive)?.content.equals("other", ignoreCase = true) }
                val url = (other?.get("url") as? JsonPrimitive)?.content
                if (url == null) Status("error", "no \"Other\" file in the result", null) else Status("success", null, url)
            }
            "failed", "not_found" -> Status("error", message ?: status, null)
            else -> Status("progress", message, null)
        }
    }

    fun download(url: String, to: File, onProgress: (Double) -> Unit = {}) = Lalal.download(url, to, onProgress)
}
