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
        click("Note reading"); waitFor("Notes on the staff (E to F)"); shot("09-levels")
        click("Level 1"); waitFor("Which note is this?"); shot("10-question")
        click("C"); waitFor("Continue"); shot("11-feedback")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("Daily exercise"); waitFor("1/", substring = true); shot("12-daily")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("Theory lessons", substring = true); shot("13-lessons")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("Progress"); waitFor("Rank"); shot("08-progress")
        click("Home"); waitFor("Start ·", substring = true)
        rule.onAllNodesWithText("Settings").fetchSemanticsNodes()
        rule.activityRule.scenario.onActivity { }
    }
}
