package app.supersax.ui

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.supersax.SaxApp
import app.supersax.domain.common.AudioRefRecord
import app.supersax.domain.song.SongSetup
import app.supersax.separation.Lalal
import app.supersax.separation.SeparationJob
import app.supersax.separation.SeparationKey
import app.supersax.separation.SeparationWorker
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The background queue: two jobs added, the worker runs them in order against a stand-in service. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SeparationQueueTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private var uploads = 0

    private fun reply(ex: com.sun.net.httpserver.HttpExchange, text: String) {
        val b = text.toByteArray(); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.use { it.write(b) }
    }

    init {
        server.createContext("/api/v1/upload/") { ex -> ex.requestBody.readBytes(); uploads++; reply(ex, """{"id":"src$uploads","name":"a","size":1,"duration":1,"expires":0}""") }
        server.createContext("/api/v1/split/stem_separator/") { ex -> val b = ex.requestBody.readBytes().decodeToString(); reply(ex, """{"task_id":"t-${if ("vocals" in b) "lead" else "wind"}"}""") }
        server.createContext("/api/v1/check/") { ex ->
            val b = ex.requestBody.readBytes().decodeToString()
            val t = if ("t-lead" in b) "t-lead" else "t-wind"
            val base = "http://127.0.0.1:${server.address.port}"
            reply(ex, """{"result":{"$t":{"source_id":"s","presets":{},"status":"success","result":{"duration":1,"tracks":[{"type":"back","label":"no_x","url":"$base/f/$t"}]}}}}""")
        }
        server.createContext("/f/") { ex -> val b = ByteArray(500) { 1 }; ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.use { it.write(b) } }
        server.start()
    }

    @After fun stop() = server.stop(0)

    @Test fun queueRunsInTheBackgroundAndLinksVersions() = runBlocking {
        Lalal.base = "http://127.0.0.1:${server.address.port}/api/v1/"
        val app = ApplicationProvider.getApplicationContext<SaxApp>()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val c = app.container
        SeparationKey.set(c, "KEY")
        val song = File(c.songsDir, "orig.m4a").apply { writeBytes(ByteArray(100) { 3 }) }
        val ref = AudioRefRecord("orig", android.net.Uri.fromFile(song).toString(), "Galaxy", 0)
        c.media.saveAudioRef(ref)
        c.songs.save("orig", SongSetup(analyzed = true, bpm = 128.0, keyTonic = 0))
        c.separationQueue.add(ref, Lalal.Stem.WIND, "no sax")
        c.separationQueue.add(ref, Lalal.Stem.LEAD, "no lead")
        assertEquals(2, c.separationQueue.all().count { it.active })

        val result = TestListenableWorkerBuilder<SeparationWorker>(app).build().doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        val jobs = c.separationQueue.all()
        assertTrue(jobs.all { it.step == SeparationJob.Step.DONE }, jobs.toString())
        assertEquals(2, uploads)
        val refs = c.media.audioRefs.first()
        val versions = refs.filter { it.title.startsWith("Galaxy (") }
        assertEquals(setOf("Galaxy (no sax)", "Galaxy (no lead)"), versions.map { it.title }.toSet())
        versions.forEach { v ->
            val s = c.songs.get(v.id)
            assertEquals("orig", s.parentId); assertEquals(128.0, s.bpm)
        }
        assertEquals(setOf("WIND", "LEAD"), versions.map { c.songs.get(it.id).removed }.toSet())
    }
}
