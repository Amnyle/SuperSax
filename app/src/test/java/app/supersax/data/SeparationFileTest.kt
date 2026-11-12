package app.supersax.data

import app.supersax.separation.fileExtension
import kotlin.test.Test
import kotlin.test.assertEquals

class SeparationFileTest {
    @Test fun extensionOnlyFromTheFileName() {
        assertEquals("mp3", fileExtension("https://d.lalal.ai/media/split/abc/back"))
        assertEquals("mp3", fileExtension("https://www.lalal.ai/me/download?id=1"))
        assertEquals("wav", fileExtension("https://cdn.example.com/x/back_track.WAV?sig=a.b"))
        assertEquals("mp3", fileExtension("https://cdn.example.com/x/back.track"))
    }
}
