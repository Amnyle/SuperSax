package app.supersax.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.supersax.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Walks the real app on a phone-sized screen and saves a PNG of each main screen (build/screens) to look at the design. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenTour {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String, substring: Boolean = false) =
        rule.waitUntil(10_000) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun click(text: String, substring: Boolean = false) {
        waitFor(text, substring)
        val node = rule.onAllNodesWithText(text, substring = substring)[0]
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun tour() {
        waitFor("Welcome to Super Sax"); shot("01-welcome")
        click("Get started"); waitFor("Which language?"); shot("02-language")
        click("Next"); waitFor("How do you name the notes?"); shot("03-note-names")
        click("Next"); shot("04-days")
        repeat(5) { click("Next") }
        waitFor("Your program"); shot("05-program")
        click("Use this program")
        waitFor("Start ·", substring = true); waitFor("This week"); shot("06-home")
        click("Training"); waitFor("On the phone"); shot("07-training")
        click("Sight-reading play-along"); waitFor("New melody"); shot("27-play-along")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("Solo practice"); waitFor("Learn the form"); shot("21-solo")
        click("Target the 3rd"); click("Play the backing"); waitFor("Aim for", substring = true); shot("22-solo-playing")
        click("Stop")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("Note reading"); waitFor("Notes on the staff (E to F)"); shot("09-levels")
        click("Start"); waitFor("0 right"); click("C"); shot("23-speed")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("Level 1"); waitFor("Which note is this?"); shot("10-question")
        click("C"); waitFor("Continue"); shot("11-feedback")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("Daily exercise"); waitFor("1/", substring = true); shot("12-daily")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("Ear training"); waitFor("Higher or lower?"); shot("24-ear-levels")
        click("Level 1"); waitFor("Is the second note higher or lower?"); shot("25-ear-question")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("Rhythm reading"); waitFor("Quarters, halves, whole notes")
        click("Level 1"); waitFor("Tap this rhythm"); shot("26-rhythm-question")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("Fingering chart"); waitFor("Middle"); shot("20-fingering-chart")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("Theory lessons", substring = true); shot("13-lessons")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        // A song already analysed (B♭ major, 112 BPM), to see the player without an audio file.
        rule.activityRule.scenario.onActivity { a ->
            val c = (a.application as app.supersax.SaxApp).container
            kotlinx.coroutines.runBlocking {
                c.media.saveAudioRef(app.supersax.domain.common.AudioRefRecord("tour-song", "file:///nonexistent.m4a", "Practice song", 0))
                c.songs.save("tour-song", app.supersax.domain.song.SongSetup(analyzed = true, detectedBpm = 112.0, bpm = 112.0, firstBeatMs = 1200,
                    keyTonic = 10, keyMinor = false, analysisVersion = app.supersax.domain.song.CURRENT_ANALYSIS,
                    checkpoints = listOf(app.supersax.domain.song.Checkpoint("c1", "Chorus", 34000))))
            }
        }
        click("Song player"); waitFor("Search YouTube"); shot("14-songs")
        click("Practice song"); waitFor("Show the key signature"); shot("17-song-player")
        click("Show the key signature"); waitFor("Written for alto"); shot("18-key-signature")
        click("Metronome clicks"); shot("19-song-mix")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("Search YouTube"); waitFor("Song or artist"); shot("15-youtube-search")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        // An update with notes not seen yet shows "What's new" once.
        rule.activityRule.scenario.onActivity { a ->
            val c = (a.application as app.supersax.SaxApp).container
            kotlinx.coroutines.runBlocking { c.settings.update { it.copy(lastWhatsNew = 0) } }
        }
        waitFor("What's new"); shot("16-whats-new")
        click("Got it")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("What's new").fetchSemanticsNodes().isEmpty() }
        click("Progress"); waitFor("Rank"); shot("08-progress")
        click("Home"); waitFor("Start ·", substring = true)
        rule.onAllNodesWithText("Settings").fetchSemanticsNodes()
        rule.activityRule.scenario.onActivity { }
    }
}
