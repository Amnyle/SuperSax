package app.supersax.data

import app.supersax.separation.Mvsep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/** Parsing MVSEP results (shape taken from a live demo result of the saxophone model). */
class MvsepClientTest {
    private fun parse(s: String) = Mvsep.parseGet(Json.parseToJsonElement(s) as JsonObject)

    @Test fun states() {
        assertEquals("progress", parse("""{"success":true,"status":"waiting","data":{"queue_count":3,"current_order":2}}""").state)
        assertEquals("progress", parse("""{"success":true,"status":"distributing","data":{}}""").state)
        val done = parse("""{"success":true,"status":"done","data":{"files":[{"type":"Saxophone","url":"https://de.mvsep.com/a_saxophone.mp3"},{"type":"Other","url":"https://de.mvsep.com/a_other.mp3"}]}}""")
        assertEquals("success", done.state); assertEquals("https://de.mvsep.com/a_other.mp3", done.backTrack)
        val failed = parse("""{"success":true,"status":"failed","data":{"message":"Audio too short"}}""")
        assertEquals("error", failed.state); assertEquals("Audio too short", failed.message)
    }
}
