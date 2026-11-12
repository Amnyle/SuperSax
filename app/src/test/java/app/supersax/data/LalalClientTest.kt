package app.supersax.data

import app.supersax.separation.Lalal
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The separation client (API v1) against a local stand-in of the service: requests, polling, results, errors. */
class LalalClientTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val seen = mutableMapOf<String, String>()
    private var checks = 0

    private fun reply(ex: com.sun.net.httpserver.HttpExchange, code: Int, text: String) {
        val b = text.toByteArray(); ex.sendResponseHeaders(code, b.size.toLong()); ex.responseBody.use { it.write(b) }
    }

    init {
        server.createContext("/api/v1/upload/") { ex ->
            seen["key"] = ex.requestHeaders.getFirst("X-License-Key")
            seen["disp"] = ex.requestHeaders.getFirst("Content-Disposition")
            seen["bytes"] = ex.requestBody.readBytes().size.toString()
            if (seen["key"] != "KEY") reply(ex, 403, """{"detail":"Invalid license key","code":"invalid_license"}""")
            else reply(ex, 200, """{"id":"src1","name":"a.m4a","size":3,"duration":1,"expires":0}""")
        }
        server.createContext("/api/v1/split/stem_separator/") { ex ->
            seen["split"] = ex.requestBody.readBytes().decodeToString()
            reply(ex, 200, """{"task_id":"task1"}""")
        }
        server.createContext("/api/v1/check/") { ex ->
            checks++
            seen["check"] = ex.requestBody.readBytes().decodeToString()
            val base = "http://127.0.0.1:${server.address.port}"
            reply(ex, 200, if (checks < 2) """{"result":{"task1":{"source_id":"src1","presets":{},"status":"progress","progress":40}}}"""
                else """{"result":{"task1":{"source_id":"src1","presets":{},"status":"success","result":{"duration":1,"tracks":[
                    {"type":"stem","label":"vocals","url":"$base/f/stem"},{"type":"back","label":"mix_no_lead","url":"$base/f/other"},
                    {"type":"back","label":"no_vocals","url":"$base/f/back"}]}}}}""")
        }
        server.createContext("/f/back") { ex -> val b = ByteArray(1000) { 7 }; ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.use { it.write(b) } }
        server.start()
    }

    @AfterTest fun stop() = server.stop(0)

    @Test fun fullRun() {
        Lalal.base = "http://127.0.0.1:${server.address.port}/api/v1/"
        val id = Lalal.upload("KEY", "abc".byteInputStream(), "Ma chanson é.m4a", 3)
        assertEquals("src1", id)
        assertTrue(seen["disp"]!!.startsWith("attachment; filename=\""), seen["disp"])
        assertEquals("3", seen["bytes"])
        val task = Lalal.split("KEY", id, Lalal.Stem.WIND)
        assertEquals("task1", task)
        val s = seen["split"]!!
        assertTrue(s.contains("\"source_id\":\"src1\"") && s.contains("\"stem\":\"wind\"") && s.contains("\"splitter\":\"phoenix\"")
            && s.contains("\"encoder_format\":\"mp3\"") && s.contains("deep_extraction"), s)
        Lalal.split("KEY", id, Lalal.Stem.LEAD)
        assertTrue(seen["split"]!!.contains("\"stem\":\"vocals\"") && seen["split"]!!.contains("\"splitter\":\"auto\""), seen["split"])
        val first = Lalal.check("KEY", task)
        assertEquals("progress", first.state); assertEquals(40, first.progress)
        assertTrue(seen["check"]!!.contains("\"task_ids\":[\"task1\"]"), seen["check"])
        val done = Lalal.check("KEY", task)
        assertEquals("success", done.state)
        assertTrue(done.backTrack!!.endsWith("/f/back"), "the instrumental back track is chosen")
        val out = File.createTempFile("back", ".mp3")
        Lalal.download(done.backTrack!!, out)
        assertEquals(1000, out.length())
    }

    @Test fun serviceErrorsArePassedOn() {
        Lalal.base = "http://127.0.0.1:${server.address.port}/api/v1/"
        val e = assertFailsWith<Lalal.ServiceException> { Lalal.upload("NOPE", "x".byteInputStream(), "a.mp3", 1) }
        assertEquals("Invalid license key", e.message)
        val err = Lalal.parseCheck(kotlinx.serialization.json.Json.parseToJsonElement(
            """{"result":{"t":{"source_id":"s","presets":{},"status":"error","error":{"detail":"File too short","code":"x"}}}}""").let { it as kotlinx.serialization.json.JsonObject }, "t")
        assertEquals("error", err.state); assertEquals("File too short", err.error)
    }
}
