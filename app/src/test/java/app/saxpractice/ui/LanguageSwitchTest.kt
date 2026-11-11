package app.saxpractice.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.saxpractice.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Runs the real app on the JVM (Robolectric): first launch → onboarding → generated program →
 * Today → Start (the "two taps" path), then opens the main screens to check they compose.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LanguageSwitchTest {
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

    /** Acceptance: switching language changes the strings; switching note names changes note labels. */
    @Test fun languageAndNoteNamesSwitch() {
        waitFor("Step 1 of 10")
        repeat(8) { click("Next") }
        waitFor("Language, note names and theme")
        click("Solfège (Do Ré Mi)")
        click("Français")
        // The activity recreates in French.
        waitFor("Suivant")
        click("Suivant")
        waitFor("Ton programme")
        click("Utiliser ce programme")
        waitFor("Démarrer ·", substring = true)
        // The first unlocked scale (written C major) is shown in solfège.
        waitFor("Do majeur", substring = true)
    }
}
