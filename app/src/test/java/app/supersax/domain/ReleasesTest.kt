package app.supersax.domain

import app.supersax.domain.update.GhAsset
import app.supersax.domain.update.GhRelease
import app.supersax.domain.update.Releases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleasesTest {
    @Test fun versionOrdering() {
        assertTrue(Releases.isNewer("v1.0.10", "1.0.9"))
        assertTrue(Releases.isNewer("1.0.1", "0.1.0"))
        assertTrue(Releases.isNewer("1.1", "1.0.99"))
        assertFalse(Releases.isNewer("1.0.9", "1.0.9"))
        assertFalse(Releases.isNewer("1.0.8", "1.0.9"))
        assertFalse(Releases.isNewer("1.0", "1.0.0"))
    }

    @Test fun pickingTheUpdate() {
        val apk = GhAsset("SuperSax-1.0.5.apk", "https://example/a.apk", 30_000_000)
        val rel = GhRelease("v1.0.5", body = "Notes", assets = listOf(GhAsset("app-debug.apk", "x", 40_000_000), apk))
        val u = Releases.available(rel, "1.0.4")!!
        assertEquals("1.0.5", u.version); assertEquals(apk, u.apk); assertEquals("Notes", u.notes)
        assertNull(Releases.available(rel, "1.0.5"))
        assertNull(Releases.available(rel.copy(draft = true), "0.1.0"))
        assertNull(Releases.available(rel.copy(prerelease = true), "0.1.0"))
        assertNull(Releases.available(rel.copy(assets = listOf(GhAsset("notes.txt", "x"))), "0.1.0"))
    }
}
