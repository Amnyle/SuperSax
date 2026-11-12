package app.supersax.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.supersax.SaxApp
import app.supersax.domain.common.DrillKind
import app.supersax.domain.drills.Levels
import app.supersax.ui.nav.LocalContainer
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.practice.DrillLessonScreen
import app.supersax.ui.practice.LevelsScreen
import app.supersax.ui.practice.PlayLevelScreen
import app.supersax.ui.theme.SaxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Opens every lesson, levels screen and level of the phone drills, so none of them crashes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fr-w411dp-h900dp-night-xhdpi")
class PhoneDrillsRenderTest {
    @get:Rule val rule = createComposeRule()

    @Test fun everyLessonAndLevelOpens() {
        val c = ApplicationProvider.getApplicationContext<SaxApp>().container
        val settings = c.settings.currentBlocking().copy(noteNaming = app.supersax.domain.pitch.NoteNaming.SOLFEGE, language = "fr")
        val screens = mutableListOf<Pair<String, @androidx.compose.runtime.Composable () -> Unit>>()
        for (k in listOf(DrillKind.READING, DrillKind.SCALE_RECOGNITION, DrillKind.FINGERING, DrillKind.EAR)) {
            screens += "levels $k" to { LevelsScreen(k, {}, {}, onLesson = {}) }
            for (l in 1..Levels.count(k, 0)) {
                screens += "lesson $k $l" to { DrillLessonScreen(k, l, {}, {}) }
                screens += "play $k $l" to { PlayLevelScreen(k, l, {}, {}) }
                if (k == DrillKind.EAR) screens += "play sax $k $l" to { PlayLevelScreen(k, l, {}, {}, sax = true) }
            }
        }
        var index by mutableStateOf(0)
        rule.setContent {
            CompositionLocalProvider(LocalContainer provides c, LocalSettings provides settings) {
                SaxTheme { screens[index].second() }
            }
        }
        for (i in screens.indices) {
            rule.runOnIdle { index = i }
            try { rule.waitForIdle() } catch (e: Throwable) { throw AssertionError("crash in ${screens[i].first}", e) }
        }
    }
}
