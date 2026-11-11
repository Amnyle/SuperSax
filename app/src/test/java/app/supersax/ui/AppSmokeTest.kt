package app.supersax.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.supersax.MainActivity
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
class AppSmokeTest {
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

    @Test fun onboardingThenTwoTapsToARunningSession() {
        waitFor("Step 1 of 9")
        waitFor("Welcome to Super Sax")
        click("Get started")
        repeat(7) { click("Next") }
        waitFor("Your program")
        click("Use this program")
        // Today: one big Start button.
        waitFor("Start ·", substring = true)
        click("Start ·", substring = true)
        waitFor("Step 1 of", substring = true)
        waitFor("Lips tired")
        rule.onNodeWithText("+1 min").performClick()
        click("End session")
        waitFor("Session done")
        click("Save")
        waitFor("Start ·", substring = true)

        // Tabs and main screens compose without crashing.
        // Home dashboard: the week and the rank.
        waitFor("This week"); waitFor("Coming up")
        click("Training"); waitFor("Tuner"); waitFor("On the phone")
        // Phone drills open on their level ladder; level 1 is open, level 2 locked.
        click("Note reading"); waitFor("Notes on the staff (E to F)")
        click("Level 1"); waitFor("Which note is this?")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("Daily exercise"); waitFor("1/", substring = true)
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.onAllNodesWithText("Library").assertCountEquals(0)
        click("Progress"); waitFor("Rank")
        click("Rank"); waitFor("Weakest pillar", substring = true)
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("Weekly review"); waitFor("Sessions:", substring = true)
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("History"); waitFor("Week of", substring = true)
    }
}
