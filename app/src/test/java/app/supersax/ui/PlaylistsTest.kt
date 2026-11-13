package app.supersax.ui

import androidx.test.core.app.ApplicationProvider
import app.supersax.SaxApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaylistsTest {
    @Test fun defaultsCreateMembersRenameDeleteForget() = runBlocking {
        val c = ApplicationProvider.getApplicationContext<SaxApp>().container
        val p = c.playlists
        assertEquals(listOf("To learn", "Learnt"), p.all.first().map { it.name })
        p.setMember("to-learn", "s1", true)
        p.setMember("to-learn", "s2", true)
        p.setMember("to-learn", "s1", true) // no duplicate
        val jazz = p.create("Jazz")
        p.setMember(jazz, "s1", true)
        assertEquals(listOf("s1", "s2"), p.all.first().first { it.id == "to-learn" }.songIds)
        p.setMember("to-learn", "s1", false)
        p.rename(jazz, "Jazz standards")
        assertTrue(p.all.first().any { it.name == "Jazz standards" && it.songIds == listOf("s1") })
        p.forget("s1")
        assertTrue(p.all.first().none { "s1" in it.songIds })
        p.delete(jazz)
        assertEquals(2, p.all.first().size)
    }
}
