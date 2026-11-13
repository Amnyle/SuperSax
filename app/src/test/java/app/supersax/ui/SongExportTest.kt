package app.supersax.ui

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.supersax.SaxApp
import app.supersax.playback.SongExport
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SongExportTest {
    @Test fun namesAndFormats() {
        val ctx = ApplicationProvider.getApplicationContext<SaxApp>()
        assertEquals("Digga-Leg (Super Mario Galaxy 2) - Mario Kart World (no sax).mp3",
            SongExport.fileName("Digga-Leg (Super Mario Galaxy 2) - Mario Kart World (no sax)", "mp3"))
        assertEquals("AC DC Back in Black.m4a", SongExport.fileName("AC/DC: Back in Black?", "m4a"))
        assertEquals("m4a", SongExport.extension(ctx, Uri.parse("file:///data/songs/abc.m4a")))
        assertEquals("mp3", SongExport.extension(ctx, Uri.parse("file:///data/songs/x.mp3")))
        assertTrue(SongExport.mime("m4a").startsWith("audio/"))
    }
}
