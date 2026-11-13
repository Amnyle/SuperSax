package app.supersax.domain

import app.supersax.domain.song.Checkpoint
import app.supersax.domain.song.SongSetup
import app.supersax.domain.song.withSharedFrom
import app.supersax.ui.songs.matches
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongFamilyTest {
    @Test fun versionTakesTheOriginalsAnalysisButKeepsItsLink() {
        val original = SongSetup(analyzed = true, bpm = 128.0, detectedBpm = 128.0, firstBeatMs = 1200, keyTonic = 2, keyMinor = true,
            analysisVersion = app.supersax.domain.song.CURRENT_ANALYSIS, checkpoints = listOf(Checkpoint("c", "Chorus", 30000)), source = "yt")
        val version = SongSetup(parentId = "orig", removed = "SAX", source = null)
        val v = version.withSharedFrom(original)
        assertTrue(v.analyzed); assertEquals(128.0, v.bpm); assertEquals(1200, v.firstBeatMs); assertEquals(2, v.keyTonic)
        assertEquals(1, v.checkpoints.size)
        assertEquals("orig", v.parentId); assertEquals("SAX", v.removed); assertEquals(null, v.source)
    }

    @Test fun searchMatchesEveryWordIgnoringCaseAndAccents() {
        assertTrue(matches("Digga-Leg (Super Mario Galaxy 2) - Mario Kart World", "mario kart"))
        assertTrue(matches("Café Bossa", "cafe"))
        assertFalse(matches("Blue Bossa", "mario"))
    }
}
