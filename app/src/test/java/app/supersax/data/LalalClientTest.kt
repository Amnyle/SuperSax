package app.supersax.data

import app.supersax.separation.Lalal
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The separation client against a local stand-in of the service: request format, polling, results, errors. */
class LalalClientTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val seen = mutableMapOf<String, String>()
    private var checks = 0

    init {
        server.createContext("/api/upload/") { ex ->
            seen["auth"] = ex.requestHeaders.getFirst("Authorization")
            seen["disp"] = ex.requestHeaders.getFirst("Content-Disposition")
            seen["bytes"] = ex.requestBody.readBytes().size.toString()
            val body = """{"status":"success","id":"f1","size":3,"duration":1}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        server.createContext("/api/split/") { ex ->
            seen["split"] = URLDecoder.decode(ex.requestBody.readBytes().decodeToString(), "UTF-8")
            val body = """{"status":"success","task_id":"t1"}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        server.createContext("/api/check/") { ex ->
            checks++
            val base = "http://127.0.0.1:${server.address.port}"
            val body = (if (checks < 2) """{"status":"success","result":{"f1":{"status":"success","task":{"state":"progress","progress":40}}}}"""
                else """{"status":"success","result":{"f1":{"status":"success","split":{"stem_track":"$base/files/stem.mp3","back_track":"$base/files/back.mp3"},"task":{"state":"success"}}}}""").toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        server.createContext("/files/back.mp3") { ex ->
            val body = ByteArray(1000) { 7 }
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        server.createContext("/bad/upload/") { ex ->
            val body = """{"status":"error","error":"Invalid license"}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @AfterTest fun stop() = server.stop(0)

    @Test fun fullRun() {
        Lalal.base = "http://127.0.0.1:${server.address.port}/api/"
        val id = Lalal.upload("KEY", "abc".byteInputStream(), "Ma chanson é.m4a", 3)
        assertEquals("f1", id)
        assertEquals("license KEY", seen["auth"])
        assertTrue(seen["disp"]!!.startsWith("attachment; filename=\""), seen["disp"])
        assertEquals("3", seen["bytes"])
        Lalal.split("KEY", id, Lalal.Stem.WIND)
        assertTrue(seen["split"]!!.contains("\"stem\":\"wind\"") && seen["split"]!!.contains("\"id\":\"f1\""), seen["split"])
        val first = Lalal.check("KEY", id)
        assertEquals("progress", first.state); assertEquals(40, first.progress)
        val done = Lalal.check("KEY", id)
        assertEquals("success", done.state)
        val out = File.createTempFile("back", ".mp3")
        Lalal.download(done.backTrack!!, out)
        assertEquals(1000, out.length())
    }

    @Test fun serviceErrorsArePassedOn() {
        Lalal.base = "http://127.0.0.1:${server.address.port}/bad/"
        val e = assertFailsWith<Lalal.ServiceException> { Lalal.upload("NOPE", "x".byteInputStream(), "a.mp3", 1) }
        assertEquals("Invalid license", e.message)
    }
}
